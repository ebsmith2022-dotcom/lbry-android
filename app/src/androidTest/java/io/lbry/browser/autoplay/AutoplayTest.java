package io.lbry.browser.autoplay;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import io.lbry.browser.R;
import android.os.Build;
@RunWith(AndroidJUnit4.class)
public class AutoplayTest {
 private void pressDialog(AutoplayTestActivity a, int button) {
  try {java.lang.reflect.Field f=AutoplayController.class.getDeclaredField("dialog");f.setAccessible(true);androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)f.get(a.controller);assertNotNull(dialog);assertTrue(dialog.isShowing());dialog.getButton(button).performClick();}catch(ReflectiveOperationException e){throw new AssertionError(e);}
 }
 @Test public void turningOffCancelsPendingAdvance() throws Exception {isolated();try(ActivityScenario<AutoplayTestActivity>a=ActivityScenario.launch(AutoplayTestActivity.class)){a.onActivity(x->x.controller.ended(x.current));Thread.sleep(500);a.onActivity(x->((androidx.appcompat.widget.SwitchCompat)x.findViewById(R.id.autoplay_next_switch)).setChecked(false));Thread.sleep(5500);a.onActivity(x->{assertEquals(0,x.opened);x.controller.ended(x.current);});Thread.sleep(500);a.onActivity(x->assertEquals(0,x.opened));}}
 private void isolated(){org.junit.Assume.assumeTrue("Emulator only: never reset a real phone's preferences",Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk_gphone"));}
 @Test public void countdownAdvancesOnce() throws Exception {isolated();try(ActivityScenario<AutoplayTestActivity>a=ActivityScenario.launch(AutoplayTestActivity.class)){a.onActivity(x->{x.controller.ended(x.current);x.controller.ended(x.current);});Thread.sleep(6500);a.onActivity(x->assertEquals(1,x.opened));}}
 @Test public void cancelPreventsAdvance() throws Exception {isolated();try(ActivityScenario<AutoplayTestActivity>a=ActivityScenario.launch(AutoplayTestActivity.class)){a.onActivity(x->x.controller.ended(x.current));Thread.sleep(500);a.onActivity(x->pressDialog(x, android.content.DialogInterface.BUTTON_NEGATIVE));Thread.sleep(5500);a.onActivity(x->assertEquals(0,x.opened));}}
 @Test public void playNowAdvances() throws Exception {isolated();try(ActivityScenario<AutoplayTestActivity>a=ActivityScenario.launch(AutoplayTestActivity.class)){a.onActivity(x->x.controller.ended(x.current));Thread.sleep(500);a.onActivity(x->pressDialog(x, android.content.DialogInterface.BUTTON_POSITIVE));a.onActivity(x->assertEquals(1,x.opened));}}
 @Test public void leavingScreenCancelsCountdown() throws Exception {isolated();try(ActivityScenario<AutoplayTestActivity>a=ActivityScenario.launch(AutoplayTestActivity.class)){a.onActivity(x->x.controller.ended(x.current));Thread.sleep(500);a.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(5500);a.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);a.onActivity(x->assertEquals(0,x.opened));}}
}
