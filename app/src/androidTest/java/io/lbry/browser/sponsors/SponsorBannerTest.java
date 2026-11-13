package io.lbry.browser.sponsors;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import io.lbry.browser.BuildConfig;
import io.lbry.browser.R;

/** Run only on a disposable emulator, built with sponsorBaseUrl=http://127.0.0.1:8787. */
@RunWith(AndroidJUnit4.class)
public class SponsorBannerTest {
    @Test public void approvedImageDisplaysThenExpiresAndStopClearsIt() throws Exception {
        org.junit.Assume.assumeTrue("Run with the local sponsor test URL", "http://127.0.0.1:8787".equals(BuildConfig.SPONSOR_BASE_URL));
        Bitmap bitmap = Bitmap.createBitmap(320, 50, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
        AtomicInteger mode = new AtomicInteger(1);
        try (ServerSocket server = new ServerSocket(8787, 8, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(2000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String first = reader.readLine(), line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                        boolean image = first != null && first.contains("/v1/images/");
                        boolean empty = !image && mode.get() == 0;
                        byte[] body = image ? png.toByteArray() : (mode.get() == 2 ? "{invalid json" : "{\"sponsor\":\"Test sponsor\",\"destination\":\"https://example.com\",\"image\":\"/v1/images/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"ttl_seconds\":1}").getBytes(StandardCharsets.UTF_8);
                        if (empty) body = new byte[0];
                        OutputStream out = socket.getOutputStream();
                        out.write(("HTTP/1.1 " + (empty ? "204 No Content" : "200 OK") + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); out.write(body); out.flush();
                    } catch (IOException ignored) { }
                }
            });
            responder.setDaemon(true); responder.start();
            final SponsorBannerController[] controller = new SponsorBannerController[1];
            final View[] view = new View[1];
            onMain(() -> {
                Context context = new android.view.ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.AppTheme);
                view[0] = LayoutInflater.from(context).inflate(R.layout.sponsor_banner, null);
                controller[0] = new SponsorBannerController(view[0]); controller[0].start();
            });
            try {
                awaitVisibility(view[0], View.VISIBLE);
                mode.set(0); awaitVisibility(view[0], View.GONE);
                mode.set(2); onMain(controller[0]::start); Thread.sleep(500); assertVisibility(view[0], View.GONE);
                mode.set(1); onMain(controller[0]::start); awaitVisibility(view[0], View.VISIBLE);
                onMain(controller[0]::stop); assertVisibility(view[0], View.GONE);
                Thread.sleep(1200); assertVisibility(view[0], View.GONE);
            } finally { onMain(controller[0]::stop); }
        }
    }
    private static void onMain(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static void assertVisibility(View view, int expected) { onMain(() -> assertEquals(expected, view.getVisibility())); }
    private static void awaitVisibility(View view, int expected) throws InterruptedException {
        AtomicInteger actual = new AtomicInteger();
        long until = System.currentTimeMillis() + 5000;
        do { onMain(() -> actual.set(view.getVisibility())); if (actual.get() == expected) return; Thread.sleep(25); } while (System.currentTimeMillis() < until);
        assertEquals(expected, actual.get());
    }
}
