package io.lbry.browser.sponsors;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import io.lbry.browser.BuildConfig;
import io.lbry.browser.R;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;

/** Optional sponsorship with delivery-scoped aggregate metrics; no wallet, account or device identifier is sent. */
public final class SponsorBannerController {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    private final View container;
    private final ImageView image;
    private final TextView label;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile Call pending;
    private boolean running;
    private long visibleSince = -1;
    private Runnable visibilityCheck;
    private final Runnable refresh = this::load;

    public SponsorBannerController(View root) {
        container = root.findViewById(R.id.sponsor_banner);
        image = root.findViewById(R.id.sponsor_image);
        label = root.findViewById(R.id.sponsor_label);
    }
    public void start() { stop(); running = true; load(); }
    public void stop() {
        running = false; generation++; main.removeCallbacksAndMessages(null);
        Call call = pending; if (call != null) call.cancel();
        hide();
    }
    private void hide() {
        if (visibilityCheck != null) main.removeCallbacks(visibilityCheck);
        visibilityCheck = null; visibleSince = -1;
        container.setVisibility(View.GONE); image.setImageDrawable(null); image.setOnClickListener(null);
    }
    private void load() {
        hide();
        if (!running) return;
        final HttpUrl base = HttpUrl.parse(BuildConfig.SPONSOR_BASE_URL);
        if (base == null || !allowedBase(base)) return;
        final int ticket = ++generation;
        // Hide and check again at least every minute, including when offline/no campaign exists.
        main.removeCallbacks(refresh); main.postDelayed(refresh, 60_000);
        IO.execute(() -> {
            try {
                byte[] document = fetch(base.newBuilder().encodedPath("/v1/banner").build(), 16_384, ticket);
                if (document == null) return;
                JSONObject ad = new JSONObject(new String(document, java.nio.charset.StandardCharsets.UTF_8));
                String sponsor = ad.getString("sponsor");
                String path = ad.getString("image");
                HttpUrl destination = HttpUrl.parse(ad.getString("destination"));
                int ttl = ad.getInt("ttl_seconds");
                final String eventToken = ad.optString("event_token", "");
                if (sponsor.isEmpty() || sponsor.length() > 80 || !path.matches("/v1/images/[a-f0-9-]{36}") ||
                        destination == null || !destination.isHttps() || !destination.username().isEmpty() || !destination.password().isEmpty() || ttl < 1 || ttl > 60) return;
                long expires = SystemClock.elapsedRealtime() + ttl * 1000L;
                byte[] bytes = fetch(base.newBuilder().encodedPath(path).build(), 2 * 1024 * 1024, ticket);
                if (bytes == null) return;
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 4_000_000) return;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) return;
                main.post(() -> {
                    if (!running || generation != ticket || SystemClock.elapsedRealtime() >= expires) return;
                    label.setText(container.getContext().getString(R.string.sponsor_label, sponsor));
                    image.setImageBitmap(bitmap);
                    image.setContentDescription(container.getContext().getString(R.string.sponsor_open, sponsor));
                    image.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
                            .setTitle(R.string.sponsor_visit_title).setMessage(destination.host())
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.sponsor_visit, (dialog, which) -> {
                                try {
                                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(destination.toString())));
                                    sendEvent(base, eventToken, "click");
                                }
                                catch (ActivityNotFoundException ignored) { /* No browser installed. */ }
                            }).show());
                    container.setVisibility(View.VISIBLE);
                    observeImpression(base, eventToken, ticket, expires);
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, Math.max(1, expires - SystemClock.elapsedRealtime()));
                });
            } catch (Exception ignored) { /* Ads are optional: never affect playback or display request data in logs. */ }
        });
    }
    static boolean isViewable(View view) {
        if (!view.isAttachedToWindow() || !view.isShown() || !view.hasWindowFocus() || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        Rect visible = new Rect();
        return view.getGlobalVisibleRect(visible) &&
                (long) visible.width() * visible.height() * 2 >= (long) view.getWidth() * view.getHeight();
    }
    private void observeImpression(HttpUrl base, String token, int ticket, long expires) {
        if (!token.matches("[a-f0-9]{64}")) return;
        visibilityCheck = new Runnable() {
            @Override public void run() {
                if (!running || generation != ticket || SystemClock.elapsedRealtime() >= expires) return;
                long now = SystemClock.elapsedRealtime();
                if (isViewable(image)) {
                    if (visibleSince < 0) visibleSince = now;
                    if (now - visibleSince >= 1000) { sendEvent(base, token, "impression"); return; }
                } else visibleSince = -1;
                main.postDelayed(this, 250);
            }
        };
        main.post(visibilityCheck);
    }
    private static void sendEvent(HttpUrl base, String token, String type) {
        if (!token.matches("[a-f0-9]{64}")) return;
        IO.execute(() -> {
            try {
                JSONObject payload = new JSONObject(); payload.put("token", token); payload.put("type", type);
                Request request = new Request.Builder().url(base.newBuilder().encodedPath("/v1/events").build())
                        .post(RequestBody.create(MediaType.parse("application/json"), payload.toString())).build();
                // Best effort; receipt deduplication handles any transport retry. No persistent event queue.
                try (Response ignored = CLIENT.newCall(request).execute()) { }
            } catch (Exception ignored) { /* Metrics must never interrupt playback or navigation. */ }
        });
    }
    private static boolean allowedBase(HttpUrl base) {
        return base.username().isEmpty() && base.password().isEmpty() && base.query() == null &&
                (base.isHttps() || (BuildConfig.DEBUG && base.host().equals("127.0.0.1")));
    }
    private byte[] fetch(HttpUrl url, int limit, int ticket) throws IOException {
        if (generation != ticket) return null;
        Call call = CLIENT.newCall(new Request.Builder().url(url).header("Cache-Control", "no-store").build());
        pending = call;
        if (generation != ticket) { call.cancel(); return null; }
        try (Response response = call.execute()) {
            if (response.code() != 200 || response.body() == null || response.body().contentLength() > limit) return null;
            try (InputStream input = response.body().byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (generation != ticket || output.size() + count > limit) return null;
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
    }
}
