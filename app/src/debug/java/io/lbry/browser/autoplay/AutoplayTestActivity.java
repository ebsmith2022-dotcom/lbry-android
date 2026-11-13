package io.lbry.browser.autoplay;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import io.lbry.browser.R;
import io.lbry.browser.model.Claim;
public class AutoplayTestActivity extends AppCompatActivity {
 public AutoplayController controller; public int opened; public boolean ended=true; public Claim current;
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.autoplay_next);
 PreferenceManager.getDefaultSharedPreferences(this).edit().clear().putBoolean(AutoplayController.ENABLED,true).commit();
 current=video("current");controller=new AutoplayController(findViewById(android.R.id.content),new AutoplayController.Host(){public boolean isEnded(String id){return ended;}public void open(Claim next){opened++;ended=false;}},(c,h,m,r)->video("next"));controller.start();controller.playing(current);}
 private Claim video(String id){Claim c=new Claim();c.setClaimId(id);c.setPermanentUrl("lbry://"+id+"#abc");Claim.StreamMetadata m=new Claim.StreamMetadata();m.setStreamType("video");m.setTitle("Next test video");c.setValue(m);return c;}
 @Override public void onPause(){controller.stop();super.onPause();}
}
