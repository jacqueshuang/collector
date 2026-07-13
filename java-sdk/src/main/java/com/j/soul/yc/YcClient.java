package com.j.soul.yc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.crypto.CrytoLogin;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.model.ApiResult;
import com.j.soul.yc.model.ReplaceMobileRequest;
import com.j.soul.yc.sign.GwSigner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class YcClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    private static final String PATH_CHECK_MOBILE = "/mobile/loginregister/checkMemberMobileAvailable";
    private static final String PATH_GET_SMS = "/mobile/login/getSmsCodeNewV2";
    private static final String PATH_REPLACE_MOBILE = "/mobile/openApi/replaceMobile";

    private final YcClientConfig config;
    private final HttpTransport transport;
    private final CaptchaProvider captchaProvider;
    private final boolean ownTransport;
    private final boolean ownCaptcha;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    YcClient(
            YcClientConfig config,
            HttpTransport transport,
            CaptchaProvider captchaProvider,
            boolean ownTransport,
            boolean ownCaptcha) {
        this.config = Objects.requireNonNull(config, "config");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.captchaProvider = Objects.requireNonNull(captchaProvider, "captchaProvider");
        this.ownTransport = ownTransport;
        this.ownCaptcha = ownCaptcha;
    }

    public static YcClientBuilder builder() {
        return new YcClientBuilder();
    }

    public ApiResult checkMobileAvailable(String mobile) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        try {
            String query = "mobile=" + urlEncode(mobile) + "&rnd=" + System.currentTimeMillis();
            String url = joinUrl(config.baseUrl(), PATH_CHECK_MOBILE) + "?" + query;
            Map<String, String> headers = businessHeaders(PATH_CHECK_MOBILE, "GET", null);
            HttpResponse resp = transport.execute(new HttpRequest("GET", url, headers, null, null));
            return parseApiResult(resp, YcStep.CHECK_MOBILE);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CHECK_MOBILE, "checkMobileAvailable failed: " + e.getMessage(), e);
        }
    }

    public CaptchaResult getCaptchaVerifyParam() {
        ensureOpen();
        try {
            return captchaProvider.getCaptchaVerifyParam();
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "getCaptchaVerifyParam failed: " + e.getMessage(), e);
        }
    }

    public ApiResult getSmsCode(String mobile, String captchaVerifyParam) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        Objects.requireNonNull(captchaVerifyParam, "captchaVerifyParam");
        try {
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("mobile", mobile);
            payload.put("pathType", 11);
            payload.put("captchaVerifyParam", captchaVerifyParam);
            payload.put("sceneId", config.sceneId());

            Map<String, String> encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64());
            byte[] body = formEncode(encrypted).getBytes(StandardCharsets.UTF_8);

            Map<String, String> headers = businessHeaders(PATH_GET_SMS, "POST", null);
            headers.put("Content-Type", FORM_CONTENT_TYPE);

            String url = joinUrl(config.baseUrl(), PATH_GET_SMS);
            HttpResponse resp = transport.execute(new HttpRequest("POST", url, headers, body, FORM_CONTENT_TYPE));
            return parseApiResult(resp, YcStep.SMS);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.SMS, "getSmsCode failed: " + e.getMessage(), e);
        }
    }

    /**
     * Full flow: check mobile → captcha → getSmsCode.
     * If check returns {@code code != 200}, returns that result without captcha/sms.
     */
    public ApiResult sendSms(String mobile) {
        ensureOpen();
        ApiResult check = checkMobileAvailable(mobile);
        if (check.getCode() == null || check.getCode() != 200) {
            return check;
        }
        CaptchaResult captcha = getCaptchaVerifyParam();
        return getSmsCode(mobile, captcha.captchaVerifyParam());
    }

    public ApiResult replaceMobile(ReplaceMobileRequest req) {
        ensureOpen();
        Objects.requireNonNull(req, "req");
        try {
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("cardNo", req.cardNo());
            payload.put("mobile", req.mobile());
            payload.put("certificatesNo", req.certNo());
            payload.put("verificationCode", req.smsCode());

            Map<String, String> encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64());
            byte[] body = formEncode(encrypted).getBytes(StandardCharsets.UTF_8);

            String token = req.accessToken();
            Map<String, String> headers = businessHeaders(PATH_REPLACE_MOBILE, "POST", token);
            headers.put("Content-Type", FORM_CONTENT_TYPE);
            if (token != null && !token.isBlank()) {
                headers.put("Authorization", "Bearer " + token);
            }

            String url = joinUrl(config.baseUrl(), PATH_REPLACE_MOBILE);
            HttpResponse resp = transport.execute(new HttpRequest("POST", url, headers, body, FORM_CONTENT_TYPE));
            return parseApiResult(resp, YcStep.REPLACE);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.REPLACE, "replaceMobile failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (ownCaptcha && captchaProvider instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
        if (ownTransport) {
            try {
                transport.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private Map<String, String> businessHeaders(String path, String method, String accessTokenOrNull) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", config.userAgent());
        headers.put("Origin", config.origin());
        headers.put("Referer", config.referer());
        headers.put("channel", config.channel());
        headers.put("client", config.client());
        headers.putAll(GwSigner.sign(
                path,
                method,
                config.gwKey(),
                config.gwClient(),
                accessTokenOrNull,
                null,
                null));
        return headers;
    }

    private static ApiResult parseApiResult(HttpResponse resp, YcStep step) {
        try {
            String json = new String(resp.body(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, ApiResult.class);
        } catch (Exception e) {
            throw new YcException(step, "failed to parse ApiResult (http " + resp.status() + ")", e);
        }
    }

    private static String formEncode(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new YcException(YcStep.HTTP, "YcClient is closed");
        }
    }
}
