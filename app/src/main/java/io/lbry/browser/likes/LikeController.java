package io.lbry.browser.likes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;
import io.lbry.browser.BuildConfig;
import io.lbry.browser.R;
import okhttp3.*;

/** Per-video pseudonymous likes. No account, wallet, or shared cross-video identifier is sent. */
public final class LikeController {
 private static final OkHttpClient CLIENT = new OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
 private final Handler main = new Handler(Looper.getMainLooper());
 private final View container;
 private final Button button;
 private final TextView status;
 private final SharedPreferences preferences;
 private String claim, token;
 private boolean running, liked, loaded, busy;
 private int generation;
 private Call call;
 private final Runnable refresh = () -> { if(running && !busy) request(null); };
 public LikeController(View root) {
  container=root.findViewById(R.id.community_likes);
  button=root.findViewById(R.id.community_like_button);
  status=root.findViewById(R.id.community_like_status);
  preferences=root.getContext().getApplicationContext().getSharedPreferences("community_likes",Context.MODE_PRIVATE);
  button.setOnClickListener(v -> {
   if(token==null && claim!=null)try{token=videoToken(claim);}catch(Exception error){failed();return;}
   request(loaded ? !liked : null);
  });
 }
 public void bind(String id) {
  if(id != null) id=id.toLowerCase(java.util.Locale.ROOT);
  if(id != null && !id.matches("[a-f0-9]{40}")) id=null;
  if(java.util.Objects.equals(id,claim)) return;
  cancel();claim=id;loaded=false;liked=false;token=null;
  container.setVisibility(id == null ? View.GONE : View.VISIBLE);
  if(id == null) return;
  button.setText(R.string.community_like_loading);button.setEnabled(false);status.setText(R.string.community_likes_scope);
  try {token=videoToken(id);} catch(Exception error){failed();return;}
  if(running) request(null);
 }
 public void start(){running=true;if(claim!=null && token!=null)request(null);}
 public void stop(){running=false;cancel();}
 private void cancel(){generation++;if(call!=null)call.cancel();call=null;busy=false;main.removeCallbacks(refresh);}
 private String videoToken(String id) throws Exception {
  String secret=preferences.getString("secret",null);
  if(secret==null){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);secret=hex(bytes);if(!preferences.edit().putString("secret",secret).commit())throw new IOException("Unable to save like identity");}
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return hex(mac.doFinal(id.getBytes(StandardCharsets.UTF_8)));
 }
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));return out.toString();}
 private void failed(){busy=false;loaded=false;button.setSelected(false);button.setEnabled(true);button.setText(R.string.community_like_retry);status.setText(R.string.community_likes_error);}
 private void request(Boolean desired) {
  if(!running || claim==null || token==null || busy)return;
  HttpUrl base=HttpUrl.parse(BuildConfig.SPONSOR_BASE_URL);
  if(base==null || !(base.isHttps() || (BuildConfig.DEBUG && base.host().equals("127.0.0.1")))){failed();return;}
  cancel();final int ticket=generation;busy=true;button.setEnabled(false);
  Request.Builder builder=new Request.Builder().url(base.newBuilder().encodedPath("/v1/likes/"+claim).query(null).build()).header("X-Like-Token",token);
  if(desired!=null) builder.post(RequestBody.create(MediaType.parse("application/json"),"{\"liked\":"+desired+"}"));
  call=CLIENT.newCall(builder.build());call.enqueue(new Callback(){
   @Override public void onFailure(Call c,IOException e){main.post(()->{if(running && generation==ticket)failed();});}
   @Override public void onResponse(Call c,Response response){
    try(Response r=response){
     if(!r.isSuccessful() || r.body()==null)throw new IOException("Unavailable");
     String payload=r.peekBody(4097).string();if(payload.length()>4096)throw new IOException("Oversized response");
     JSONObject data=new JSONObject(payload);final long count=data.getLong("count");final boolean selected=data.getBoolean("liked");
     if(count<0 || (selected && count<1))throw new IOException("Invalid count");
     main.post(()->{if(!running || generation!=ticket)return;busy=false;loaded=true;liked=selected;
      button.setEnabled(true);button.setSelected(selected);
      button.setText(button.getContext().getString(selected?R.string.community_liked_count:R.string.community_like_count,java.text.NumberFormat.getIntegerInstance().format(count)));
      button.setContentDescription(button.getContext().getString(selected?R.string.community_unlike_accessibility:R.string.community_like_accessibility,count));
      status.setText(R.string.community_likes_scope);main.postDelayed(refresh,60_000);
     });
    }catch(Exception e){main.post(()->{if(running && generation==ticket)failed();});}
   }
  });
 }
}
