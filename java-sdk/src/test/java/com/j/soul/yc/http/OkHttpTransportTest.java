package com.j.soul.yc.http;

import com.j.soul.yc.config.YcClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkHttpTransportTest {

    @Test
    void okHttp_postsBodyAndHeaders() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> xTest = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            method.set(exchange.getRequestMethod());
            xTest.set(exchange.getRequestHeaders().getFirst("X-Test"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/echo";
            YcClientConfig cfg = YcClientConfig.builder().build();
            try (var t = new OkHttpTransport(cfg)) {
                var resp = t.execute(new HttpRequest(
                        "POST",
                        url,
                        Map.of("X-Test", "1"),
                        "hello".getBytes(StandardCharsets.UTF_8),
                        "text/plain"));
                assertEquals(200, resp.status());
                assertEquals("ok", new String(resp.body(), StandardCharsets.UTF_8));
            }
            assertEquals("POST", method.get());
            assertEquals("1", xTest.get());
            assertEquals("hello", body.get());
            assertTrue(contentType.get() != null && contentType.get().startsWith("text/plain"));
        } finally {
            server.stop(0);
        }
    }
}
