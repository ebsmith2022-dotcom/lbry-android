package io.lbry.browser.likes;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Assume;
import static org.junit.Assert.*;
import android.widget.Button;
import io.lbry.browser.BuildConfig;
import io.lbry.browser.R;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LikesTest {
 @Test public void likeUnlikePersistAndSeparateVideos() throws Exception {
  Assume.assumeTrue("Local isolated endpoint required",BuildConfig.SPONSOR_BASE_URL.equals("http://127.0.0.1:8787"));
  Set<String> likes=Collections.synchronizedSet(new HashSet<>());
  Map<String,String> tokens=Collections.synchronizedMap(new HashMap<>());
  try(ServerSocket server=new ServerSocket(8787,8,InetAddress.getByName("127.0.0.1"))){
   Thread worker=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
    socket.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
    String first=in.readLine(),line,token="";int length=0;
    while((line=in.readLine())!=null&&!line.isEmpty()){String lower=line.toLowerCase(Locale.ROOT);if(lower.startsWith("x-like-token:"))token=line.substring(13).trim();if(lower.startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
    char[] body=new char[length];int read=0,n;while(read<length&&(n=in.read(body,read,length-read))>0)read+=n;
    String id=first.split(" ")[1];tokens.put(id,token);String key=id+token;
    if(first.startsWith("POST")){if(new String(body).contains("true"))likes.add(key);else likes.remove(key);}
    boolean selected=likes.contains(key);int count=0;for(String entry:likes)if(entry.startsWith(id))count++;
    byte[] json=("{\"count\":"+count+",\"liked\":"+selected+"}").getBytes(StandardCharsets.UTF_8);
    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+json.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));socket.getOutputStream().write(json);
   }catch(Exception ignored){}});worker.setDaemon(true);worker.start();
   String firstToken;
   try(ActivityScenario<LikesTestActivity> activity=ActivityScenario.launch(LikesTestActivity.class)){
    awaitLabel(activity,"♡ Like · 0");activity.onActivity(a->a.findViewById(R.id.community_like_button).performClick());awaitLabel(activity,"♥ Liked · 1");
    firstToken=tokens.get("/v1/likes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");assertTrue(firstToken.matches("[a-f0-9]{64}"));
    activity.onActivity(a->a.controller.bind("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));awaitLabel(activity,"♡ Like · 0");
    assertNotEquals(firstToken,tokens.get("/v1/likes/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    activity.onActivity(a->a.controller.bind("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));awaitLabel(activity,"♥ Liked · 1");
   }
   try(ActivityScenario<LikesTestActivity> activity=ActivityScenario.launch(LikesTestActivity.class)){
    awaitLabel(activity,"♥ Liked · 1");assertEquals(firstToken,tokens.get("/v1/likes/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    activity.onActivity(a->a.findViewById(R.id.community_like_button).performClick());awaitLabel(activity,"♡ Like · 0");
    server.close();activity.onActivity(a->{a.controller.stop();a.controller.start();});awaitLabel(activity,"Retry likes");
   }
  }
 }
 private static void awaitLabel(ActivityScenario<LikesTestActivity> activity,String wanted)throws Exception{
  AtomicReference<String> label=new AtomicReference<>();long until=System.currentTimeMillis()+15000;
  do{activity.onActivity(a->label.set(((Button)a.findViewById(R.id.community_like_button)).getText().toString()));if(wanted.equals(label.get()))return;Thread.sleep(40);}while(System.currentTimeMillis()<until);
  assertEquals(wanted,label.get());
 }
}
