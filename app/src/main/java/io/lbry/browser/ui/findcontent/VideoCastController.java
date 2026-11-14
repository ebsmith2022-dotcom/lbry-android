package io.lbry.browser.ui.findcontent;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.Toast;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.function.Function;
import java.util.function.Supplier;
import io.lbry.browser.MainActivity;
import io.lbry.browser.R;
import io.lbry.browser.model.Claim;

/** Sends only public media URLs to the default receiver, never wallet or account data. */
final class VideoCastController implements SessionManagerListener<CastSession> {
    private final Activity activity;
    private final Supplier<Claim> claimSupplier;
    private final Function<Claim, Uri> resolveStream;
    private SessionManager sessions;
    private boolean closed;
    private boolean loading;
    private int generation;
    private RemoteMediaClient observedRemote;
    private SimpleExoPlayer castLocalPlayer;
    private Claim castClaim;
    private final RemoteMediaClient.Callback remoteCallback = new RemoteMediaClient.Callback() {
        @Override public void onStatusUpdated() {
            if (observedRemote != null && observedRemote.getMediaStatus() != null
                    && observedRemote.getMediaStatus().getIdleReason()
                    == com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR) {
                message(R.string.community_cast_failed);
            }
        }
    };

    VideoCastController(Activity activity, View root, Supplier<Claim> claimSupplier,
                        Function<Claim, Uri> resolveStream) {
        this.activity = activity;
        this.claimSupplier = claimSupplier;
        this.resolveStream = resolveStream;
        try {
            sessions = CastContext.getSharedInstance(activity).getSessionManager();
            CastButtonFactory.setUpMediaRouteButton(activity,
                    (MediaRouteButton) root.findViewById(R.id.video_cast_picker));
            sessions.addSessionManagerListener(this, CastSession.class);
            root.findViewById(R.id.video_cast_play).setOnClickListener(v -> castVideo());
            root.findViewById(R.id.video_cast_here).setOnClickListener(v -> playHere());
        } catch (RuntimeException exception) {
            // Devices without Google Play services must retain normal playback.
            root.findViewById(R.id.video_cast_bar).setVisibility(View.GONE);
        }
    }

    private void message(int resource) {
        if (!closed) Toast.makeText(activity, resource, Toast.LENGTH_LONG).show();
    }

    private void castVideo() {
        if (closed || loading || sessions == null) return;
        CastSession session = sessions.getCurrentCastSession();
        if (session == null || !session.isConnected() || session.getRemoteMediaClient() == null) {
            message(R.string.community_cast_connect);
            return;
        }
        Claim claim = claimSupplier.get();
        SimpleExoPlayer player = MainActivity.appPlayer;
        if (claim == null || !claim.isFree() || !claim.isPlayable() || player == null
                || MainActivity.nowPlayingClaim == null
                || !claim.getClaimId().equals(MainActivity.nowPlayingClaim.getClaimId())) {
            message(R.string.community_cast_free);
            return;
        }
        loading = true;
        int request = ++generation;
        message(R.string.community_cast_loading);
        MediaItem item = player.getCurrentMediaItem();
        Uri current = item != null && item.localConfiguration != null
                ? item.localConfiguration.uri : null;
        // Never send a loopback URL or local file path to a receiver.
        AsyncTask.execute(() -> {
            Uri stream = isPublicStream(current) ? current : resolveStream.apply(claim);
            activity.runOnUiThread(() -> {
                if (closed || request != generation) return;
                if (sessions.getCurrentCastSession() != session || !session.isConnected()
                        || MainActivity.appPlayer != player || claimSupplier.get() != claim) {
                    loading = false;
                    return;
                }
                if (!isPublicStream(stream)) {
                    loading = false;
                    message(R.string.community_cast_failed);
                    return;
                }
                MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
                metadata.putString(MediaMetadata.KEY_TITLE,
                        claim.getTitle() != null ? claim.getTitle() : claim.getName());
                String path = stream.getPath();
                String contentType = CastMediaPolicy.contentType(path, claim.getMediaType());
                MediaInfo media = new MediaInfo.Builder(stream.toString())
                        .setContentType(contentType).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                        .setMetadata(metadata).build();
                RemoteMediaClient remote = session.getRemoteMediaClient();
                if (remote == null) { loading = false; return; }
                if (observedRemote != null) observedRemote.unregisterCallback(remoteCallback);
                observedRemote = remote;
                remote.registerCallback(remoteCallback);
                remote.load(new MediaLoadRequestData.Builder().setMediaInfo(media)
                        .setAutoplay(true).setCurrentTime(Math.max(0, player.getCurrentPosition()))
                        .build()).setResultCallback(result -> {
                    if (closed || request != generation) return;
                    loading = false;
                    if (result.getStatus().isSuccess()) {
                        if (MainActivity.appPlayer == player && claimSupplier.get() == claim) {
                            castLocalPlayer = player;
                            castClaim = claim;
                            player.pause();
                        }
                        message(R.string.community_cast_ready);
                    } else {
                        message(R.string.community_cast_failed);
                    }
                });
            });
        });
    }

    static boolean isPublicStream(Uri uri) {
        return uri != null && CastMediaPolicy.isPublicStream(uri.toString());
    }

    private void playHere() {
        if (loading) { message(R.string.community_cast_loading); return; }
        generation++;
        loading = false;
        SimpleExoPlayer player = MainActivity.appPlayer;
        Claim claim = claimSupplier.get();
        if (player == null || claim == null || MainActivity.nowPlayingClaim == null
                || !claim.getClaimId().equals(MainActivity.nowPlayingClaim.getClaimId())) return;
        if (observedRemote != null && player == castLocalPlayer && claim == castClaim
                && observedRemote.hasMediaSession()) {
            long position = observedRemote.getApproximateStreamPosition();
            observedRemote.pause();
            player.seekTo(Math.max(0, position));
        }
        player.play();
    }

    void close() {
        closed = true;
        generation++;
        if (observedRemote != null) observedRemote.unregisterCallback(remoteCallback);
        if (sessions != null) sessions.removeSessionManagerListener(this, CastSession.class);
    }

    @Override public void onSessionStarted(CastSession session, String id) { castVideo(); }
    @Override public void onSessionStartFailed(CastSession session, int error) { message(R.string.community_cast_failed); }
    @Override public void onSessionResumeFailed(CastSession session, int error) { message(R.string.community_cast_failed); }
    @Override public void onSessionEnded(CastSession session, int error) { generation++; loading = false; }
    @Override public void onSessionStarting(CastSession session) { }
    @Override public void onSessionEnding(CastSession session) { }
    @Override public void onSessionResuming(CastSession session, String id) { }
    @Override public void onSessionResumed(CastSession session, boolean suspended) { }
    @Override public void onSessionSuspended(CastSession session, int reason) { generation++; loading = false; }
}
