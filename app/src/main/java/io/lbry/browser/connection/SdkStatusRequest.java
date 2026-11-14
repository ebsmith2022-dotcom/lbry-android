package io.lbry.browser.connection;

import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/** Bounded, cancellable local health check; does not change wallet state. */
public final class SdkStatusRequest {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();

    public static Call newCall() {
        return newCall("http://127.0.0.1:5379");
    }

    static Call newCall(String endpoint) {
        return CLIENT.newCall(new Request.Builder().url(endpoint)
                .post(RequestBody.create(MediaType.parse("application/json"),
                        "{\"method\":\"status\"}"))
                .build());
    }

    private SdkStatusRequest() { }
}
