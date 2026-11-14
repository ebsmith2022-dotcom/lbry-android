package io.lbry.browser.ui.findcontent;

import java.net.URI;
import java.net.URISyntaxException;

final class CastMediaPolicy {
    private CastMediaPolicy() { }

    static boolean isPublicStream(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme())
                    && "player.odycdn.com".equals(uri.getRawAuthority());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    static String contentType(String path, String sourceType) {
        if (path != null && path.endsWith(".m3u8")) return "application/x-mpegURL";
        if (path != null && path.endsWith(".mpd")) return "application/dash+xml";
        if (sourceType != null && (sourceType.startsWith("video/") || sourceType.startsWith("audio/")))
            return sourceType;
        return "video/mp4";
    }
}
