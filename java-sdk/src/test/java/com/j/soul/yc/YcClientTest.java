package com.j.soul.yc;

import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.model.ApiResult;
import com.j.soul.yc.model.ReplaceMobileRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YcClientTest {

    @Test
    void sendSms_stopsOnCheckFailure() {
        RecordingTransport transport = new RecordingTransport(req ->
                json(200, "{\"code\":400,\"msg\":\"bad mobile\",\"data\":null}"));
        AtomicInteger captchaCalls = new AtomicInteger();
        CaptchaProvider captcha = () -> {
            captchaCalls.incrementAndGet();
            return new CaptchaResult("param", "scene", "cid", "stok");
        };

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(captcha)
                .build()) {
            ApiResult r = client.sendSms("13800138000");
            assertEquals(400, r.getCode());
            assertEquals("bad mobile", r.getMsg());
        }

        assertEquals(1, transport.requests.size());
        assertEquals(0, captchaCalls.get());
        HttpRequest check = transport.requests.getFirst();
        assertEquals("GET", check.method());
        assertTrue(check.url().contains("/mobile/loginregister/checkMemberMobileAvailable"));
        assertTrue(check.url().contains("mobile=13800138000"));
        assertTrue(check.url().contains("rnd="));
        assertHasBusinessHeaders(check.headers());
    }

    @Test
    void sendSms_happyPath_callsCaptchaAndSms() {
        AtomicInteger captchaCalls = new AtomicInteger();
        CaptchaProvider captcha = () -> {
            captchaCalls.incrementAndGet();
            return new CaptchaResult("cvp-token", "1pn9314j", "cid-1", "sec-1");
        };

        RecordingTransport transport = new RecordingTransport(req -> {
            if (req.url().contains("checkMemberMobileAvailable")) {
                return json(200, "{\"code\":200,\"msg\":\"ok\",\"data\":true}");
            }
            if (req.url().contains("getSmsCodeNewV2")) {
                return json(200, "{\"code\":200,\"msg\":\"sms-ok\",\"data\":null}");
            }
            return json(404, "{\"code\":404,\"msg\":\"unexpected\",\"data\":null}");
        });

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(captcha)
                .build()) {
            ApiResult r = client.sendSms("13900001111");
            assertEquals(200, r.getCode());
            assertEquals("sms-ok", r.getMsg());
        }

        assertEquals(1, captchaCalls.get());
        assertEquals(2, transport.requests.size());

        HttpRequest check = transport.requests.get(0);
        assertEquals("GET", check.method());
        assertTrue(check.url().contains("checkMemberMobileAvailable"));

        HttpRequest sms = transport.requests.get(1);
        assertEquals("POST", sms.method());
        assertTrue(sms.url().endsWith("/mobile/login/getSmsCodeNewV2"));
        assertHasBusinessHeaders(sms.headers());
        assertTrue(sms.headers().get("Content-Type").startsWith("application/x-www-form-urlencoded"));
        String body = new String(sms.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("key="));
        assertTrue(body.contains("data="));
        assertFalse(body.contains("captchaVerifyParam=")); // encrypted form, not plain
        assertNotNull(sms.headers().get("GW-Signature"));
    }

    @Test
    void sendLoginSms_skipsCheckAndUsesLoginFlow() {
        AtomicInteger captchaCalls = new AtomicInteger();
        CaptchaProvider captcha = () -> {
            captchaCalls.incrementAndGet();
            return new CaptchaResult("cvp-login", "1pn9314j", "cid-l", "sec-l");
        };

        RecordingTransport transport = new RecordingTransport(req -> {
            if (req.url().contains("getSmsCodeNewV2")) {
                return json(200, "{\"code\":200,\"message\":\"验证码已发送\",\"data\":null}");
            }
            return json(404, "{\"code\":404,\"msg\":\"unexpected\",\"data\":null}");
        });

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(captcha)
                .build()) {
            ApiResult r = client.sendLoginSms("13900002222");
            assertEquals(200, r.getCode());
            assertEquals("验证码已发送", r.getMsg());
        }

        assertEquals(1, captchaCalls.get());
        assertEquals(1, transport.requests.size(), "login SMS must not call checkMemberMobileAvailable");
        HttpRequest sms = transport.requests.getFirst();
        assertEquals("POST", sms.method());
        assertTrue(sms.url().endsWith("/mobile/login/getSmsCodeNewV2"));
        assertTrue(sms.headers().get("Referer").contains("isShow=login"));
        assertHasBusinessHeaders(sms.headers());
        String body = new String(sms.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("key="));
        assertTrue(body.contains("data="));
    }

    @Test
    void replaceMobile_sendsEncryptedForm() {
        RecordingTransport transport = new RecordingTransport(req ->
                json(200, "{\"code\":200,\"msg\":\"replaced\",\"data\":null}"));

        ReplaceMobileRequest req = new ReplaceMobileRequest(
                "CARD001",
                "13800138000",
                "110101199001011234",
                "123456",
                "access-token-abcdefgh");

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
                .build()) {
            ApiResult r = client.replaceMobile(req);
            assertEquals(200, r.getCode());
            assertEquals("replaced", r.getMsg());
        }

        assertEquals(1, transport.requests.size());
        HttpRequest post = transport.requests.getFirst();
        assertEquals("POST", post.method());
        assertTrue(post.url().endsWith("/mobile/openApi/replaceMobile"));
        assertHasBusinessHeaders(post.headers());
        assertEquals("Bearer access-token-abcdefgh", post.headers().get("Authorization"));
        assertNotNull(post.headers().get("GW-Signature"));
        String body = new String(post.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("key="));
        assertTrue(body.contains("data="));
        assertFalse(body.contains("cardNo="));
        assertTrue(post.headers().get("Content-Type").startsWith("application/x-www-form-urlencoded"));
    }

    @Test
    void checkMobileAvailable_attachesGwAndChannelHeaders() {
        RecordingTransport transport = new RecordingTransport(req ->
                json(200, "{\"code\":200,\"msg\":\"ok\",\"data\":true}"));

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
                .build()) {
            ApiResult r = client.checkMobileAvailable("13700000000");
            assertEquals(200, r.getCode());
        }

        HttpRequest req = transport.requests.getFirst();
        assertHasBusinessHeaders(req.headers());
        assertNotNull(req.headers().get("GW-Timestamp"));
        assertNotNull(req.headers().get("GW-Nonce"));
        assertNotNull(req.headers().get("GW-Client"));
    }

    @Test
    void loginBySms_postsEncryptedFormWithBasicAuth() {
        RecordingTransport transport = new RecordingTransport(req ->
                json(200, "{\"code\":200,\"msg\":\"login-ok\",\"data\":{\"access_token\":\"tok\"}}"));

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
                .build()) {
            ApiResult r = client.loginBySms("13900003333", "654321");
            assertEquals(200, r.getCode());
            assertEquals("login-ok", r.getMsg());
            assertNotNull(r.getData());
        }

        assertEquals(1, transport.requests.size());
        HttpRequest post = transport.requests.getFirst();
        assertEquals("POST", post.method());
        assertTrue(post.url().endsWith("/login"), post.url());
        assertEquals(
                "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==",
                post.headers().get("Authorization"));
        assertFalse(post.headers().get("Authorization").startsWith("Bearer"));
        assertTrue(post.headers().get("Referer").contains("isShow=login"));
        assertHasBusinessHeaders(post.headers());
        assertNotNull(post.headers().get("GW-Timestamp"));
        assertNotNull(post.headers().get("GW-Nonce"));
        assertNotNull(post.headers().get("GW-Client"));
        assertTrue(post.headers().get("Content-Type").startsWith("application/x-www-form-urlencoded"));
        String body = new String(post.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("key="));
        assertTrue(body.contains("data="));
        assertFalse(body.contains("username="));
        assertFalse(body.contains("password="));
        assertFalse(body.contains("auth_type="));
    }

    @Test
    void loginBySms_businessReject_returnsApiResult() {
        RecordingTransport transport = new RecordingTransport(req ->
                json(200, "{\"code\":401,\"msg\":\"bad code\",\"data\":null}"));

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
                .build()) {
            ApiResult r = client.loginBySms("13900003333", "000000");
            assertEquals(401, r.getCode());
            assertEquals("bad code", r.getMsg());
        }
    }

    @Test
    void loginBySms_transportFailure_usesLoginStep() {
        RecordingTransport transport = new RecordingTransport(req -> {
            throw new RuntimeException("boom");
        });

        try (YcClient client = YcClient.builder()
                .config(YcClientConfig.builder().baseUrl("https://example.test/api").build())
                .transport(transport)
                .captchaProvider(() -> new CaptchaResult("x", "s", "c", "t"))
                .build()) {
            com.j.soul.yc.exception.YcException ex =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            com.j.soul.yc.exception.YcException.class,
                            () -> client.loginBySms("13900003333", "654321"));
            assertEquals(com.j.soul.yc.exception.YcStep.LOGIN, ex.getStep());
        }
    }

    private static void assertHasBusinessHeaders(Map<String, String> headers) {
        assertEquals("pc", headers.get("channel"));
        assertEquals("mall", headers.get("client"));
        assertNotNull(headers.get("User-Agent"));
        assertNotNull(headers.get("Origin"));
        assertNotNull(headers.get("Referer"));
        assertNotNull(headers.get("GW-Signature"));
    }

    private static HttpResponse json(int status, String body) {
        return new HttpResponse(status, Map.of("Content-Type", "application/json"), body.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingTransport implements HttpTransport {
        private final Function<HttpRequest, HttpResponse> handler;
        private final List<HttpRequest> requests = new ArrayList<>();

        RecordingTransport(Function<HttpRequest, HttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            requests.add(request);
            return handler.apply(request);
        }

        @Override
        public void close() {
        }
    }
}
