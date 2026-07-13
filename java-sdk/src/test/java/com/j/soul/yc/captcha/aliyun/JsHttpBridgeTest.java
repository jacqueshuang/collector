package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsHttpBridgeTest {

    @Test
    void httpWorker_doesNotInvokeCallback_untilDrainOnOwnerThread() throws Exception {
        CountDownLatch httpStarted = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(HttpRequest request) {
                httpStarted.countDown();
                try {
                    allowComplete.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new HttpResponse(200, Map.of("content-type", "text/plain"),
                        "ok".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void close() {
            }
        };

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-http");
            t.setDaemon(true);
            return t;
        });
        JsHttpBridge bridge = new JsHttpBridge(transport, exec, "ua", "https://o", "https://r");

        try (Context ctx = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .build()) {
            ctx.getBindings("js").putMember("__javaHttp", bridge);
            ctx.eval("js", "globalThis.__cbCount=0; globalThis.__cbBody=null;");
            Value jsCb = ctx.eval("js",
                    "(function(status, body, headers){ globalThis.__cbCount++; globalThis.__cbBody=body; })");

            bridge.request("GET", "https://example.test/", "", null, jsCb);

            assertTrue(httpStarted.await(3, TimeUnit.SECONDS), "worker should start HTTP");
            // Worker still blocked: nothing drained yet
            assertEquals(0, bridge.drainCallbacks());
            assertEquals(0, ctx.getBindings("js").getMember("__cbCount").asInt());

            allowComplete.countDown();
            // Wait until worker enqueues completion
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (bridge.pendingCount() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, bridge.pendingCount());
            // Still no JS callback until owner drains
            assertEquals(0, ctx.getBindings("js").getMember("__cbCount").asInt());

            int drained = bridge.drainCallbacks();
            assertEquals(1, drained);
            assertEquals(0, bridge.pendingCount());
            assertEquals(1, ctx.getBindings("js").getMember("__cbCount").asInt());
            assertEquals("ok", ctx.getBindings("js").getMember("__cbBody").asString());
        } finally {
            allowComplete.countDown();
            exec.shutdownNow();
        }
    }
}
