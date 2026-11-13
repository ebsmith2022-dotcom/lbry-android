package io.lbry.browser.autoplay;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import io.lbry.browser.utils.Lbryio;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Uses the same reported-view service as the video's displayed view count. */
public final class AutoplayViewCounts {
    public static final int MINIMUM_VIEWS = 10;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .callTimeout(4, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS).build();
    private AutoplayViewCounts() {}
    public static boolean qualifies(Object count) {
        if (!(count instanceof Number) && !(count instanceof String)) return false;
        try {
            BigDecimal value = new BigDecimal(count.toString());
            return value.stripTrailingZeros().scale() <= 0
                    && value.compareTo(BigDecimal.valueOf(MINIMUM_VIEWS)) >= 0;
        } catch (NumberFormatException error) { return false; }
    }
    public static boolean hasEnoughViews(String claimId) {
        // An anonymous app token is sufficient; never create accounts just for autoplay.
        String token = Lbryio.AUTH_TOKEN;
        if (claimId == null || token == null || token.isEmpty()) return false;
        HttpUrl base = HttpUrl.parse(Lbryio.CONNECTION_STRING + "/file/view_count");
        HttpUrl url = base.newBuilder().addQueryParameter("claim_id", claimId)
                .addQueryParameter(Lbryio.AUTH_TOKEN_PARAM, token).build();
        try (Response response = CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) return false;
            Object data = Lbryio.parseResponse(response);
            return data instanceof JSONArray && ((JSONArray) data).length() > 0
                    && qualifies(((JSONArray) data).get(0));
        } catch (Exception ignored) {
            // Missing, failed, or malformed counts never qualify. Do not log token-bearing URLs.
            return false;
        }
    }
}
