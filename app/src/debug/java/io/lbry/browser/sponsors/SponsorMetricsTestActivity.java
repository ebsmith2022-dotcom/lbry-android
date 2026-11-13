package io.lbry.browser.sponsors;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import io.lbry.browser.R;

/** Non-exported, debug-only instrumentation fixture. No wallet or account initialization. */
public class SponsorMetricsTestActivity extends Activity {
    public ScrollView scroll;
    private SponsorBannerController controller;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        View spacer = new View(this); column.addView(spacer, new LinearLayout.LayoutParams(-1, 3000));
        getLayoutInflater().inflate(R.layout.sponsor_banner, column, true);
        scroll.addView(column); setContentView(scroll);
        controller = new SponsorBannerController(column);
    }
    @Override public void onResume() { super.onResume(); controller.start(); }
    @Override public void onPause() { controller.stop(); super.onPause(); }
}
