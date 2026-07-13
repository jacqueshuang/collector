package com.j.soul.yc;

import com.j.soul.yc.captcha.CaptchaProvider;
import com.j.soul.yc.captcha.aliyun.AliyunCaptchaProvider;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.http.Curl4jTransport;
import com.j.soul.yc.http.HttpTransport;
import com.j.soul.yc.http.OkHttpTransport;
import com.j.soul.yc.http.TransportType;

import java.util.Objects;

public final class YcClientBuilder {
    private YcClientConfig config;
    private HttpTransport transport;
    private CaptchaProvider captchaProvider;

    YcClientBuilder() {
    }

    public YcClientBuilder config(YcClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        return this;
    }

    public YcClientBuilder transport(HttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        return this;
    }

    public YcClientBuilder captchaProvider(CaptchaProvider captchaProvider) {
        this.captchaProvider = Objects.requireNonNull(captchaProvider, "captchaProvider");
        return this;
    }

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
