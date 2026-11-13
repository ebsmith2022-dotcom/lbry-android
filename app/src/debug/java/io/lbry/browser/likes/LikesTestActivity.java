package io.lbry.browser.likes;
import android.app.Activity;
import android.os.Bundle;
import io.lbry.browser.R;
public class LikesTestActivity extends Activity {
 public LikeController controller;
 @Override public void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.community_likes);controller=new LikeController(findViewById(R.id.community_likes));controller.bind("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");}
 @Override public void onResume(){super.onResume();controller.start();}
 @Override public void onPause(){controller.stop();super.onPause();}
}
