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
import com.j.soul.yc.model.SmsPathType;
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
    private static final String PATH_LOGIN = "/login";
    /** Frontend OAuth client Basic for portal login (portal_app:perfect_portal). */
    private static final String PORTAL_BASIC_AUTH =
            "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==";
    /** Referer used by login-page SMS (isShow=login). */
    private static final String LOGIN_REFERER =
            "https://uc.perfect99.com/loginAndRegistration?isShow=login";

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

    /**
     * Request SMS with reset-phone pathType (11). Prefer {@link #getSmsCode(String, String, SmsPathType)}.
     */
    public ApiResult getSmsCode(String mobile, String captchaVerifyParam) {
        return getSmsCode(mobile, captchaVerifyParam, SmsPathType.RESET_PHONE);
    }

    /**
     * POST {@code /mobile/login/getSmsCodeNewV2} with crytoLogin body.
     *
     * @param pathType {@link SmsPathType#RESET_PHONE} (11) or {@link SmsPathType#LOGIN} (5)
     */
    public ApiResult getSmsCode(String mobile, String captchaVerifyParam, SmsPathType pathType) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        Objects.requireNonNull(captchaVerifyParam, "captchaVerifyParam");
        Objects.requireNonNull(pathType, "pathType");
        try {
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("mobile", mobile);
            payload.put("pathType", pathType.value());
            payload.put("captchaVerifyParam", captchaVerifyParam);
            payload.put("sceneId", config.sceneId());

            Map<String, String> encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64());
            byte[] body = formEncode(encrypted).getBytes(StandardCharsets.UTF_8);

            String referer = pathType == SmsPathType.LOGIN ? LOGIN_REFERER : config.referer();
            Map<String, String> headers = businessHeaders(PATH_GET_SMS, "POST", null, referer);
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
     * Reset-phone SMS full flow: check mobile → captcha → getSmsCode(pathType=11).
     * If check returns {@code code != 200}, returns that result without captcha/sms.
     */
    public ApiResult sendSms(String mobile) {
        ensureOpen();
        ApiResult check = checkMobileAvailable(mobile);
        if (check.getCode() == null || check.getCode() != 200) {
            return check;
        }
        CaptchaResult captcha = getCaptchaVerifyParam();
        return getSmsCode(mobile, captcha.captchaVerifyParam(), SmsPathType.RESET_PHONE);
    }

    /**
     * Login-page SMS full flow ({@code isShow=login}): captcha → getSmsCode(pathType=5).
     * No checkMemberMobileAvailable (frontend login does not call it before SMS).
     */
    public ApiResult sendLoginSms(String mobile) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        CaptchaResult captcha = getCaptchaVerifyParam();
        return getSmsCode(mobile, captcha.captchaVerifyParam(), SmsPathType.LOGIN);
    }

    /**
     * Login-page SMS submit: POST {@code /login} with crytoLogin body.
     * Payload: username=mobile, password=smsCode, auth_type=sms, grant_type=password.
     * Authorization is raw Basic (no Bearer). GW sign uses the Basic value as accessToken
     * (frontend {@code ce()} treats Authorization length &gt; 7 the same way).
     */
    public ApiResult loginBySms(String mobile, String smsCode) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        Objects.requireNonNull(smsCode, "smsCode");
        try {
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("username", mobile);
            payload.put("password", smsCode);
            payload.put("auth_type", "sms");
            payload.put("grant_type", "password");

            Map<String, String> encrypted = CrytoLogin.encrypt(payload, config.rsaPublicKeyBase64());
            byte[] body = formEncode(encrypted).getBytes(StandardCharsets.UTF_8);

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", config.userAgent());
            headers.put("Origin", config.origin());
            headers.put("Referer", LOGIN_REFERER);
            headers.put("channel", config.channel());
            headers.put("client", config.client());
            headers.put("Content-Type", FORM_CONTENT_TYPE);
            headers.put("Authorization", PORTAL_BASIC_AUTH);
            headers.putAll(GwSigner.sign(
                    PATH_LOGIN,
                    "POST",
                    config.gwKey(),
                    config.gwClient(),
                    PORTAL_BASIC_AUTH,
                    null,
                    null));

            String url = joinUrl(config.baseUrl(), PATH_LOGIN);
            HttpResponse resp = transport.execute(new HttpRequest("POST", url, headers, body, FORM_CONTENT_TYPE));
            return parseApiResult(resp, YcStep.LOGIN);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.LOGIN, "loginBySms failed: " + e.getMessage(), e);
        }
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
        return businessHeaders(path, method, accessTokenOrNull, config.referer());
    }

    private Map<String, String> businessHeaders(
            String path, String method, String accessTokenOrNull, String referer) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", config.userAgent());
        headers.put("Origin", config.origin());
        headers.put("Referer", referer == null || referer.isBlank() ? config.referer() : referer);
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
