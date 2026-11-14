package io.lbry.browser.ui.findcontent;

import org.junit.Test;
import static org.junit.Assert.*;

public class CastMediaPolicyTest {
    @Test public void onlyPublicCdnUrlsReachReceiver() {
        assertTrue(CastMediaPolicy.isPublicStream("https://player.odycdn.com/api/v4/streams/free/master.m3u8?x=1"));
        for (String url : new String[] {null, "file:///sdcard/video.mp4", "http://127.0.0.1:5280/stream",
                "https://192.168.1.1/video", "http://player.odycdn.com/video", "https://player.odycdn.com.evil.org/video",
                "https://player.odycdn.com@evil.org/video", "https://user:secret@player.odycdn.com/video",
                "https://player.odycdn.com:1234/video", "https://player.odycdn.com/invalid space"}) {
            assertFalse(url, CastMediaPolicy.isPublicStream(url));
        }
    }
    @Test public void adaptiveManifestOverridesOriginalFileType() {
        assertEquals("application/x-mpegURL", CastMediaPolicy.contentType("/master.m3u8", "video/mp4"));
        assertEquals("application/dash+xml", CastMediaPolicy.contentType("/manifest.mpd", "video/mp4"));
        assertEquals("video/webm", CastMediaPolicy.contentType("/stream", "video/webm"));
        assertEquals("audio/mpeg", CastMediaPolicy.contentType("/stream", "audio/mpeg"));
        assertEquals("video/mp4", CastMediaPolicy.contentType(null, null));
    }
}
