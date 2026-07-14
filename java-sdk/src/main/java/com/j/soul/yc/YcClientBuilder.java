package com.j.soul.yc;

import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.aliyun.AliyunCaptchaProvider;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.http.Curl4jTransport;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.http.OkHttpTransport;
import com.j.soul.yc.http.TransportType;

import java.util.Objects;

/**
 * {@link YcClient} 构建器。
 * <p>
 * 默认按配置创建 OkHttp/Curl 传输与阿里云滑块实现；也可注入自定义 transport / captcha。
 */
public final class YcClientBuilder {
    private YcClientConfig config;
    private HttpTransport transport;
    private CaptchaProvider captchaProvider;

    YcClientBuilder() {
    }

    /** 运行时配置（含 reconDir、并发、超时等）。 */
    public YcClientBuilder config(YcClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        return this;
    }

    /** 注入自定义 HTTP 传输；注入后 {@link YcClient#close()} 不会关闭它。 */
    public YcClientBuilder transport(HttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        return this;
    }

    /** 注入自定义滑块实现；注入后 {@link YcClient#close()} 不会关闭它。 */
    public YcClientBuilder captchaProvider(CaptchaProvider captchaProvider) {
        this.captchaProvider = Objects.requireNonNull(captchaProvider, "captchaProvider");
        return this;
    }

    /** 构建客户端。未设置 config 时使用默认配置。 */
    public YcClient build() {
        YcClientConfig cfg = config != null ? config : YcClientConfig.builder().build();
        boolean ownTransport = transport == null;
        HttpTransport t = ownTransport ? createTransport(cfg) : transport;
        boolean ownCaptcha = captchaProvider == null;
        CaptchaProvider captcha = ownCaptcha ? new AliyunCaptchaProvider(cfg, t) : captchaProvider;
        return new YcClient(cfg, t, captcha, ownTransport, ownCaptcha);
    }

    private static HttpTransport createTransport(YcClientConfig cfg) {
        TransportType type = cfg.transportType() == null ? TransportType.OKHTTP : cfg.transportType();
        return switch (type) {
            case CURL4J -> new Curl4jTransport(cfg);
            case OKHTTP -> new OkHttpTransport(cfg);
        };
    }
}
