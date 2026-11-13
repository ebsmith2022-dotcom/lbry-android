package io.lbry.browser.autoplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;
import java.util.*;
import io.lbry.browser.MainActivity;
import io.lbry.browser.R;
import io.lbry.browser.model.Claim;
import io.lbry.browser.utils.Helper;
import io.lbry.browser.utils.Lbry;
import io.lbry.browser.utils.Lighthouse;
import io.lbry.browser.utils.Predefined;

/** Owns one foreground video screen; invalidates every async result on navigation. */
public final class AutoplayController {
    public interface Host { boolean isEnded(String claimId); void open(Claim next); }
    interface CandidateLoader { Claim load(Claim current, List<String> recent, boolean mature, boolean random) throws Exception; }
    private final CandidateLoader loader;
    public static final String ENABLED = "community.autoplay.enabled";
    public static final String RANDOM = "community.autoplay.random";
    private static final String RECENT = "community.autoplay.recent";
    private final Context context;
    private final SharedPreferences prefs;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SwitchCompat toggle;
    private final TextView mode;
    private boolean active;
    private int generation;
    private String handled;
    private AlertDialog dialog;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (p, key) -> {
        if (ENABLED.equals(key) || RANDOM.equals(key) || MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT.equals(key)) {
            cancel(); render();
        }
    };
    public AutoplayController(View root, Host host) { this(root, host, AutoplayController::selectCandidate); }
    AutoplayController(View root, Host host, CandidateLoader loader) {
        this.loader = loader;
        context = root.getContext(); prefs = PreferenceManager.getDefaultSharedPreferences(context); this.host = host;
        toggle = root.findViewById(R.id.autoplay_next_switch);
        mode = root.findViewById(R.id.autoplay_next_mode);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(ENABLED, checked).apply();
            if (!checked) cancel();
        });
        mode.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle(R.string.autoplay_mode)
                .setSingleChoiceItems(new String[]{context.getString(R.string.autoplay_related), context.getString(R.string.autoplay_random)},
                    prefs.getBoolean(RANDOM, true) ? 1 : 0, (d, which) -> {
                        prefs.edit().putBoolean(RANDOM, which == 1).apply(); d.dismiss(); render();
                    }).setNegativeButton(android.R.string.cancel, null).show());
        render();
    }
    private void render() {
        toggle.setChecked(prefs.getBoolean(ENABLED, false));
        mode.setText(prefs.getBoolean(RANDOM, true) ? R.string.autoplay_random : R.string.autoplay_related);
    }
    public void start() { active = true; prefs.registerOnSharedPreferenceChangeListener(changes); render(); }
    public void stop() { active = false; prefs.unregisterOnSharedPreferenceChangeListener(changes); cancel(); }
    public void cancel() { generation++; handler.removeCallbacksAndMessages(null); if (dialog != null) { dialog.dismiss(); dialog = null; } }
    public void playing(Claim current) {
        if (current == null || current.getClaimId() == null) return;
        if (current.getClaimId().equals(handled)) { cancel(); handled = null; }
        List<String> recent = recent();
        recent.remove(current.getClaimId()); recent.add(0, current.getClaimId());
        while (recent.size() > 50) recent.remove(recent.size() - 1);
        prefs.edit().putString(RECENT, android.text.TextUtils.join(",", recent)).apply();
    }
    private List<String> recent() {
        String value = prefs.getString(RECENT, "");
        return new ArrayList<>(value.isEmpty() ? Collections.emptyList() : Arrays.asList(value.split(",")));
    }
    private boolean valid(int request, String current) {
        return active && generation == request && prefs.getBoolean(ENABLED, false) && host.isEnded(current);
    }
    public void ended(Claim current) {
        if (!active || current == null || current.getClaimId() == null || !prefs.getBoolean(ENABLED, false)
                || current.getClaimId().equals(handled) || !host.isEnded(current.getClaimId())) return;
        handled = current.getClaimId(); cancel();
        final int request = generation;
        final String id = current.getClaimId();
        final boolean mature = prefs.getBoolean(MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT, false);
        final boolean random = prefs.getBoolean(RANDOM, true);
        final List<String> history = recent();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            Claim next = null;
            try {
                next = loader.load(current, history, mature, random);
            } catch (Exception ignored) { /* No purchase, retry loop or navigation on network failure. */ }
            final Claim selected = next;
            handler.post(() -> {
                if (!valid(request, id)) return;
                if (selected == null || Helper.isClaimBlocked(selected)) {
                    Toast.makeText(context, R.string.autoplay_unavailable, Toast.LENGTH_LONG).show(); return;
                }
                countdown(selected, id, request);
            });
        });
    }
    private static Claim selectCandidate(Claim current, List<String> history, boolean mature, boolean random) throws Exception {
        String id = current.getClaimId();
                List<Claim> candidates = new ArrayList<>();
                if (!random) {
                    try { candidates.addAll(Lighthouse.search(current.getTitle(), 30, 0, mature, id)); }
                    catch (Exception ignored) { /* Discovery is the fallback when related search fails. */ }
                }
                if (random || candidates.stream().noneMatch(c -> AutoplayPolicy.eligible(c, id, history, mature))) {
                    Map<String,Object> options = new HashMap<>();
                    options.put("page", 1 + new Random().nextInt(5)); options.put("page_size", 50);
                    options.put("claim_type", "stream"); options.put("stream_types", Collections.singletonList("video"));
                    options.put("fee_amount", "<=0"); options.put("order_by", Collections.singletonList("release_time"));
                    if (!mature) options.put("not_tags", Predefined.MATURE_TAGS);
                    candidates.addAll(Lbry.claimSearch(options, Lbry.LBRY_TV_CONNECTION_STRING));
                }
                Collections.shuffle(candidates);
                int attempts = 0;
                for (Claim c : candidates) {
                    if (!AutoplayPolicy.eligible(c, id, history, mature)) continue;
                    if (++attempts > 5) break;
                    Claim resolved = Lbry.resolve(c.getPermanentUrl(), Lbry.LBRY_TV_CONNECTION_STRING);
                    if (AutoplayPolicy.eligible(resolved, id, history, mature)) { return resolved; }
                }
        return null;
    }
    private void countdown(Claim next, String current, int request) {
        dialog = new AlertDialog.Builder(context).setTitle(R.string.autoplay_up_next)
                .setMessage(context.getString(R.string.autoplay_countdown, next.getTitle(), 5))
                .setPositiveButton(R.string.autoplay_play_now, (d,w) -> play(next, current, request))
                .setNegativeButton(android.R.string.cancel, (d,w) -> cancel())
                .setOnCancelListener(d -> cancel()).create();
        dialog.show();
        handler.postDelayed(new Runnable() {
            int seconds = 4;
            public void run() {
                if (!valid(request, current)) { cancel(); return; }
                if (seconds == 0) { play(next, current, request); return; }
                if (dialog != null) dialog.setMessage(context.getString(R.string.autoplay_countdown, next.getTitle(), seconds--));
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }
    private void play(Claim next, String current, int request) {
        if (!valid(request, current) || !AutoplayPolicy.eligible(next, current, recent(),
                prefs.getBoolean(MainActivity.PREFERENCE_KEY_SHOW_MATURE_CONTENT, false)) || Helper.isClaimBlocked(next)) { cancel(); return; }
        cancel(); host.open(next);
    }
}
