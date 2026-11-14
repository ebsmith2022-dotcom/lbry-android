package io.lbry.browser.connection;

import org.junit.Test;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Response;
import static org.junit.Assert.*;

public class SdkStatusRequestTest {
    @Test public void stalledDaemonDoesNotBlockNextCheck() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            String endpoint = "http://127.0.0.1:" + server.getLocalPort();
            long started = System.nanoTime();
            Call stalled = SdkStatusRequest.newCall(endpoint);
            try {
                stalled.execute().close();
                fail("A daemon that never replies must time out");
            } catch (IOException expected) {
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000);
            }
            try (Socket abandoned = server.accept()) { }
            Thread reply = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.getInputStream().read(new byte[4096]);
                    byte[] body = "{\"result\":{\"startup_status\":{\"wallet\":true,\"file_manager\":true}}}".getBytes("UTF-8");
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                } catch (IOException ignored) { }
            });
            reply.setDaemon(true);
            reply.start();
            try (Response response = SdkStatusRequest.newCall(endpoint).execute()) {
                assertTrue(response.isSuccessful());
                assertTrue(response.body().string().contains("startup_status"));
            }
            reply.join(1000);
        }
    }

    @Test public void leavingScreenCancelsInFlightCheck() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(3000);
            Call call = SdkStatusRequest.newCall("http://127.0.0.1:" + server.getLocalPort());
            java.util.concurrent.FutureTask<Boolean> result = new java.util.concurrent.FutureTask<>(() -> {
                try (Response ignored = call.execute()) { return false; }
                catch (IOException expected) { return call.isCanceled(); }
            });
            Thread worker = new Thread(result);
            worker.setDaemon(true);
            worker.start();
            try (Socket waiting = server.accept()) {
                call.cancel();
                assertTrue(result.get(1, TimeUnit.SECONDS));
            }
        }
    }
}
