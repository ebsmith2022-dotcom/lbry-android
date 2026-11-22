package io.lbry.browser.summary;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import io.lbry.browser.BuildConfig;
import io.lbry.browser.R;
import io.lbry.browser.model.Claim;
import io.lbry.browser.utils.Helper;

/** Debug-only owner trial through USB loopback; never sends account or wallet data. */
public final class VideoSummaryController {
    private final View root;
    private final View button;
    private final TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build();
    private Claim claim;
    private Call call;
    private int generation;
    private boolean active;
    private AlertDialog dialog;
    private long started;

    public VideoSummaryController(View root) {
        this.root = root;
        button = root.findViewById(R.id.video_summary_button);
        status = root.findViewById(R.id.video_summary_status);
        boolean enabled = BuildConfig.DEBUG && "http://127.0.0.1:8766".equals(BuildConfig.AI_SUMMARY_BASE_URL);
        root.findViewById(R.id.video_summary_container).setVisibility(enabled ? View.VISIBLE : View.GONE);
        button.setEnabled(false);
        button.setOnClickListener(v -> confirm());
    }

    public void bind(Claim next) {
        stop();
        claim = next;
        active = true;
        button.setEnabled(next != null && next.isPlayable() && next.isFree());
        status.setText(R.string.video_summary_scope);
    }

    public void start() { active = true; }

    public void stop() {
        active = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (call != null) { call.cancel(); call = null; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        button.setEnabled(claim != null && claim.isPlayable() && claim.isFree());
        status.setText(R.string.video_summary_scope);
    }

    private void confirm() {
        if (!active || claim == null) return;
        dialog = new AlertDialog.Builder(root.getContext())
                .setTitle(R.string.video_summary_title)
                .setMessage(R.string.video_summary_consent)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.video_summary_start, (d, w) -> submit()).show();
    }

    private void submit() {
        if (!active || claim == null || !BuildConfig.DEBUG || BuildConfig.AI_SUMMARY_BASE_URL.isEmpty()) return;
        int request = ++generation;
        started = android.os.SystemClock.elapsedRealtime();
        button.setEnabled(false);
        status.setText(R.string.video_summary_connecting);
        try {
            JSONObject body = new JSONObject().put("uri", "lbry://" + claim.getName() + "#" + claim.getClaimId());
            request(new Request.Builder().url(BuildConfig.AI_SUMMARY_BASE_URL + "/jobs")
                    .post(RequestBody.create(body.toString(), Helper.JSON_MEDIA_TYPE)), request, true);
        } catch (Exception exception) { fail(request, root.getContext().getString(R.string.video_summary_error)); }
    }

    private void poll(String job, int request) {
        if (!active || request != generation) return;
        if (android.os.SystemClock.elapsedRealtime() - started > 15 * 60_000) {
            fail(request, root.getContext().getString(R.string.video_summary_timeout));
            return;
        }
        request(new Request.Builder().url(BuildConfig.AI_SUMMARY_BASE_URL + "/jobs/" + job), request, false);
    }

    private void request(Request.Builder builder, int request, boolean submitting) {
        call = client.newCall(builder.header("X-LBRY-Local-Test", "1").build());
        call.enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) {
                handler.post(() -> fail(request, root.getContext().getString(R.string.video_summary_error)));
            }
            @Override public void onResponse(Call c, Response response) {
                try (Response r = response) {
                    JSONObject data = new JSONObject(r.body() != null ? r.body().string() : "{}");
                    boolean ok = r.isSuccessful();
                    handler.post(() -> {
                        if (!active || generation != request) return;
                        if (!ok || "error".equals(data.optString("status"))) {
                            fail(request, data.optString("error", root.getContext().getString(R.string.video_summary_error)));
                            return;
                        }
                        if (submitting) {
                            String job = data.optString("job");
                            if (!job.matches("[a-f0-9]{32}")) {
                                fail(request, root.getContext().getString(R.string.video_summary_error));
                                return;
                            }
                            poll(job, request);
                        } else if ("done".equals(data.optString("status"))) {
                            button.setEnabled(true);
                            JSONObject result = data.optJSONObject("result");
                            if (result == null || result.optString("summary").isEmpty()) {
                                fail(request, root.getContext().getString(R.string.video_summary_error));
                                return;
                            }
                            status.setText(R.string.video_summary_complete);
                            String message = root.getContext().getString(R.string.video_summary_result_scope,
                                    result.optInt("seconds")) + "\n\n" + result.optString("summary")
                                    + "\n\n" + root.getContext().getString(R.string.video_summary_warning);
                            dialog = new AlertDialog.Builder(root.getContext()).setTitle(R.string.video_summary_title)
                                    .setMessage(message).setPositiveButton(android.R.string.ok, null).show();
                        } else {
                            status.setText(data.optString("stage", "Working locally…"));
                            String job = c.request().url().pathSegments().get(1);
                            handler.postDelayed(() -> poll(job, request), 2000);
                        }
                    });
                } catch (Exception exception) {
                    handler.post(() -> fail(request, root.getContext().getString(R.string.video_summary_error)));
                }
            }
        });
    }

    private void fail(int request, String message) {
        if (!active || request != generation) return;
        button.setEnabled(true);
        status.setText(message);
    }
}
