package io.lbry.browser.sponsors;

import android.graphics.Bitmap;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
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

@RunWith(AndroidJUnit4.class)
public class SponsorMetricsTest {
    @Test public void onlyVisibleBannerReportsOneImpressionPerDelivery() throws Exception {
        org.junit.Assume.assumeTrue("Requires local sponsor URL", "http://127.0.0.1:8787".equals(BuildConfig.SPONSOR_BASE_URL));
        Bitmap bitmap = Bitmap.createBitmap(320, 50, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
        AtomicInteger impressions = new AtomicInteger(), images = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(8787, 8, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(2000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String first = reader.readLine(), line; int length = 0;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                        }
                        char[] body = new char[length]; int total = 0, count;
                        while (total < length && (count = reader.read(body, total, length - total)) != -1) total += count;
                        boolean event = first != null && first.startsWith("POST /v1/events");
                        boolean image = first != null && first.contains("/v1/images/");
                        if (event && new String(body).contains("impression")) impressions.incrementAndGet();
                        if (image) images.incrementAndGet();
                        byte[] response = event ? new byte[0] : image ? png.toByteArray() : ("{\"sponsor\":\"Metrics test\",\"destination\":\"https://example.com\",\"image\":\"/v1/images/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"event_token\":\"" + new String(new char[64]).replace('\0', 'a') + "\",\"ttl_seconds\":60}").getBytes(StandardCharsets.UTF_8);
                        OutputStream out = socket.getOutputStream(); out.write(("HTTP/1.1 " + (event ? "204 No Content" : "200 OK") + "\r\nContent-Length: " + response.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); out.write(response); out.flush();
                    } catch (IOException ignored) { }
                }
            }); responder.setDaemon(true); responder.start();
            try (ActivityScenario<SponsorMetricsTestActivity> activity = ActivityScenario.launch(SponsorMetricsTestActivity.class)) {
                waitForCount(images, 1); Thread.sleep(1500);
                assertEquals("Off-screen image must not count", 0, impressions.get());
                activity.onActivity(a -> a.scroll.fullScroll(View.FOCUS_DOWN));
                waitForCount(impressions, 1);
                Thread.sleep(800); assertEquals("Dwell timer must not duplicate", 1, impressions.get());
                activity.onActivity(a -> a.scroll.fullScroll(View.FOCUS_UP)); Thread.sleep(400);
                activity.onActivity(a -> a.scroll.fullScroll(View.FOCUS_DOWN)); Thread.sleep(1300);
                assertEquals("Scrolling back is the same delivery", 1, impressions.get());
            }
        }
    }
    private static void waitForCount(AtomicInteger value, int expected) throws InterruptedException {
        long until = System.currentTimeMillis() + 8000;
        while (value.get() < expected && System.currentTimeMillis() < until) Thread.sleep(25);
        assertEquals(expected, value.get());
    }
}
