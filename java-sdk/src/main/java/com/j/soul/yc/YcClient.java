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

/**
 * Perfect99 业务客户端：检查手机号、过滑块、发短信、短信登录、换绑手机号。
 * <p>
 * 通过 {@link #builder()} 构造；需配置 {@code reconDir} 才能过阿里云滑块。
 * 业务拒绝返回 {@link ApiResult}；基础设施失败抛 {@link YcException}。
 */
public final class YcClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    private static final String PATH_CHECK_MOBILE = "/mobile/loginregister/checkMemberMobileAvailable";
    private static final String PATH_GET_SMS = "/mobile/login/getSmsCodeNewV2";
    private static final String PATH_REPLACE_MOBILE = "/mobile/openApi/replaceMobile";
    private static final String PATH_LOGIN = "/login";
    /** 门户登录 Basic（portal_app:perfect_portal）。 */
    private static final String PORTAL_BASIC_AUTH =
            "Basic cG9ydGFsX2FwcDpwZXJmZWN0X3BvcnRhbA==";
    /** 登录页短信 / 登录提交使用的 Referer（isShow=login）。 */
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

    /** 创建客户端构建器。 */
    public static YcClientBuilder builder() {
        return new YcClientBuilder();
    }

    /**
     * 检查会员手机号是否可用。
     * <p>
     * 上游：{@code GET /mobile/loginregister/checkMemberMobileAvailable}。
     * 用于重置手机号流程；登录页发码不需要调用本接口。
     *
     * @param mobile 手机号
     * @return 业务结果；{@code code==200} 表示可用
     * @throws YcException 网络/解析失败时 step={@link YcStep#CHECK_MOBILE}
     */
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

    /**
     * 完成阿里云滑块，生成发短信所需的 {@code captchaVerifyParam}。
     * <p>
     * 默认 HtmlUnit 全 DOM，单次约 40–60 秒；受 {@code captchaConcurrency} 槽位限制。
     *
     * @return 滑块结果，业务发码取 {@link CaptchaResult#captchaVerifyParam()}
     * @throws YcException 滑块失败时 step={@link YcStep#CAPTCHA}
     */
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
     * 请求短信验证码（重置手机号场景，pathType=11）。
     * <p>
     * 需调用方自行提供已通过的 {@code captchaVerifyParam}。完整流程请用 {@link #sendSms(String)}。
     *
     * @param mobile              手机号
     * @param captchaVerifyParam  滑块参数
     * @return 业务结果
     * @see #getSmsCode(String, String, SmsPathType)
     */
    public ApiResult getSmsCode(String mobile, String captchaVerifyParam) {
        return getSmsCode(mobile, captchaVerifyParam, SmsPathType.RESET_PHONE);
    }

    /**
     * 请求短信验证码（可指定场景）。
     * <p>
     * 上游：{@code POST /mobile/login/getSmsCodeNewV2}，body 为 crytoLogin 加密表单。
     *
     * @param mobile              手机号
     * @param captchaVerifyParam  滑块参数
     * @param pathType            {@link SmsPathType#RESET_PHONE}(11) 重置手机号；
     *                            {@link SmsPathType#LOGIN}(5) 登录页
     * @return 业务结果
     * @throws YcException 网络/加密/解析失败时 step={@link YcStep#SMS}
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
     * 重置手机号完整发码：检查手机号 → 过滑块 → 发短信（pathType=11）。
     * <p>
     * 若检查手机号 {@code code != 200}，直接返回检查结果，不再过滑块/发码。
     *
     * @param mobile 手机号
     * @return 业务结果
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
     * 登录页完整发码：过滑块 → 发短信（pathType=5）。
     * <p>
     * 对齐前端 {@code isShow=login}，不调用检查手机号接口。
     * 收码后用 {@link #loginBySms(String, String)} 提交验证码登录。
     *
     * @param mobile 手机号
     * @return 业务结果
     */
    public ApiResult sendLoginSms(String mobile) {
        ensureOpen();
        Objects.requireNonNull(mobile, "mobile");
        CaptchaResult captcha = getCaptchaVerifyParam();
        return getSmsCode(mobile, captcha.captchaVerifyParam(), SmsPathType.LOGIN);
    }

    /**
     * 登录页提交短信验证码（短信登录）。
     * <p>
     * 上游：{@code POST /login}。明文 payload 为
     * {@code username=手机号, password=短信码, auth_type=sms, grant_type=password}（无 cardNo），
     * 经 crytoLogin 加密后提交。Authorization 为门户 raw Basic（非 Bearer）；
     * GW 签名将 Basic 整串作为 accessToken（对齐前端 ce()）。
     * <p>
     * 成功时 {@code data} 通常含 {@code access_token} 等字段；SDK 不写 cookie、不缓存 token。
     *
     * @param mobile  手机号（与发码号码一致）
     * @param smsCode 短信验证码
     * @return 业务结果
     * @throws YcException 网络/加密/解析失败时 step={@link YcStep#LOGIN}
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

    /**
     * 换绑手机号（会员卡 + 证件 + 短信验证码）。
     * <p>
     * 上游：{@code POST /mobile/openApi/replaceMobile}。
     * 若 {@link ReplaceMobileRequest#accessToken()} 非空，请求带 {@code Authorization: Bearer ...}。
     *
     * @param req 换绑参数
     * @return 业务结果
     * @throws YcException 网络/加密/解析失败时 step={@link YcStep#REPLACE}
     */
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

    /**
     * 关闭客户端自建的 captcha / HTTP 资源。
     * <p>
     * 若通过 builder 注入了外部 transport 或 captchaProvider，则不会关闭它们。
     */
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
