package com.j.soul.yc.batch;

import com.j.soul.yc.YcClient;
import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YcSmsBatchExecutorTest {

    @Test
    void batch_sendSms_runsInParallelAndPreservesOrder() {
        AtomicInteger captchaInFlight = new AtomicInteger();
        AtomicInteger captchaMax = new AtomicInteger();
        AtomicInteger captchaCalls = new AtomicInteger();

        CaptchaProvider captcha = () -> {
            int n = captchaInFlight.incrementAndGet();
            captchaMax.accumulateAndGet(n, Math::max);
            captchaCalls.incrementAndGet();
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                captchaInFlight.decrementAndGet();
            }
            return new CaptchaResult("p", "1pn9314j", "c", "s");
        };

        ConcurrentHashMap<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(HttpRequest req) {
                String url = req.url();
                if (url.contains("checkMemberMobileAvailable")) {
                    String mobile = url.replaceAll(".*mobile=(\\d+).*", "$1");
                    hits.computeIfAbsent(mobile, k -> new AtomicInteger()).incrementAndGet();
                    return json(200, "{\"code\":200,\"msg\":\"ok\",\"data\":true}");
                }
                if (url.contains("getSmsCodeNewV2")) {
                    return json(200, "{\"code\":200,\"msg\":\"sms-ok\",\"data\":null}");
                }
                return json(500, "{\"code\":500,\"msg\":\"bad\",\"data\":null}");
            }

            @Override
            public void close() {
            }
        };

        List<String> mobiles = List.of("13800000001", "13800000002", "13800000003", "13800000004",
                "13800000005", "13800000006");

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder()
                        .baseUrl("https://example.test/api")
                        .captchaConcurrency(3)
                        .build())
                .transport(transport)
                .captchaProvider(captcha)
                .build();
             YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder()
                     .client(client)
                     .workers(6)
                     .build()) {

            long t0 = System.currentTimeMillis();
            SmsBatchReport report = batch.sendSms(mobiles);
            long elapsed = System.currentTimeMillis() - t0;

            assertEquals(6, report.total());
            assertEquals(6, report.successCount());
            assertEquals(0, report.failureCount());
            for (int i = 0; i < mobiles.size(); i++) {
                assertEquals(mobiles.get(i), report.results().get(i).mobile());
                assertTrue(report.results().get(i).success());
            }
            assertEquals(6, captchaCalls.get());
            // With 3 captcha slots and 80ms each, 6 tasks should overlap.
            assertTrue(captchaMax.get() >= 2, "expected parallel captcha, max=" + captchaMax.get());
            // Serial would be ~480ms+; parallel with 3 should be well under that.
            assertTrue(elapsed < 450, "expected parallel speedup, elapsed=" + elapsed);
        }
    }

    @Test
    void batch_recordsFailuresWithoutAbortingOthers() {
        CaptchaProvider captcha = () -> new CaptchaResult("p", "1pn9314j", "c", "s");
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(HttpRequest req) {
                if (req.url().contains("mobile=13800000002")) {
                    return json(200, "{\"code\":400,\"msg\":\"bad\",\"data\":null}");
                }
                if (req.url().contains("checkMemberMobileAvailable")) {
                    return json(200, "{\"code\":200,\"msg\":\"ok\",\"data\":true}");
                }
                return json(200, "{\"code\":200,\"msg\":\"sms-ok\",\"data\":null}");
            }

            @Override
            public void close() {
            }
        };

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(captcha)
                .build();
             YcSmsBatchExecutor batch = YcSmsBatchExecutor.builder().client(client).workers(3).build()) {

            SmsBatchReport report = batch.sendSms(List.of("13800000001", "13800000002", "13800000003"));
            assertEquals(3, report.total());
            assertEquals(2, report.successCount());
            assertEquals(1, report.failureCount());
            assertEquals("13800000002", report.failures().getFirst().mobile());
        }
    }

    private static HttpResponse json(int status, String body) {
        return new HttpResponse(status, java.util.Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }
}
