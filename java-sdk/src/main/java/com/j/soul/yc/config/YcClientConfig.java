package com.j.soul.yc.config;

import com.j.soul.yc.http.TransportType;

import java.time.Duration;
import java.util.Objects;

public final class YcClientConfig {
    private final String baseUrl;
    private final String userAgent;
    private final String channel;
    private final String client;
    private final String referer;
    private final String origin;
    private final String gwKey;
    private final String gwClient;
    private final String rsaPublicKeyBase64;
    private final String captchaPrefix;
    private final String sceneId;
    private final TransportType transportType;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    private YcClientConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.userAgent = builder.userAgent;
        this.channel = builder.channel;
        this.client = builder.client;
        this.referer = builder.referer;
        this.origin = builder.origin;
        this.gwKey = builder.gwKey;
        this.gwClient = builder.gwClient;
        this.rsaPublicKeyBase64 = builder.rsaPublicKeyBase64;
        this.captchaPrefix = builder.captchaPrefix;
        this.sceneId = builder.sceneId;
        this.transportType = builder.transportType;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String userAgent() {
        return userAgent;
    }

    public String channel() {
        return channel;
    }

    public String client() {
        return client;
    }

    public String referer() {
        return referer;
    }

    public String origin() {
        return origin;
    }

    public String gwKey() {
        return gwKey;
    }

    public String gwClient() {
        return gwClient;
    }

    public String rsaPublicKeyBase64() {
        return rsaPublicKeyBase64;
    }

    public String captchaPrefix() {
        return captchaPrefix;
    }

    public String sceneId() {
        return sceneId;
    }

    public TransportType transportType() {
        return transportType;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public static final class Builder {
        private String baseUrl = "https://uc.perfect99.com/api";
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";
        private String channel = "pc";
        private String client = "mall";
        private String referer = "https://uc.perfect99.com/loginAndRegistration?isShow=resetPhone&channel=memberCardLogin";
        private String origin = "https://uc.perfect99.com";
        private String gwKey = "8294e640299aae744184b3a529cd1e2f";
        private String gwClient = "mall_pc";
        private String rsaPublicKeyBase64 = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDf3n7GvYCjevA+JEnMQHfxDX/ePSviRR2C2tsNSVyuTm6TfaP/HLzNbAO0kK+52nr2HO2LzsSd+a98V4n5npYDWPqbswXzKLj73kBlBI0P6Uf3uygCAZtfd9qkAn0DkgGpVw1VtCb33svBkaQinOYB550OygDM1vemuQYq11E/mQIDAQAB";
        private String captchaPrefix = "1uu8u2";
        private String sceneId = "1pn9314j";
        private TransportType transportType = TransportType.OKHTTP;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl);
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = Objects.requireNonNull(userAgent);
            return this;
        }

        public Builder channel(String channel) {
            this.channel = Objects.requireNonNull(channel);
            return this;
        }

        public Builder client(String client) {
            this.client = Objects.requireNonNull(client);
            return this;
        }

        public Builder referer(String referer) {
            this.referer = Objects.requireNonNull(referer);
            return this;
        }

        public Builder origin(String origin) {
            this.origin = Objects.requireNonNull(origin);
            return this;
        }

        public Builder gwKey(String gwKey) {
            this.gwKey = Objects.requireNonNull(gwKey);
            return this;
        }

        public Builder gwClient(String gwClient) {
            this.gwClient = Objects.requireNonNull(gwClient);
            return this;
        }

        public Builder rsaPublicKeyBase64(String rsaPublicKeyBase64) {
            this.rsaPublicKeyBase64 = Objects.requireNonNull(rsaPublicKeyBase64);
            return this;
        }

        public Builder captchaPrefix(String captchaPrefix) {
            this.captchaPrefix = Objects.requireNonNull(captchaPrefix);
            return this;
        }

        public Builder sceneId(String sceneId) {
            this.sceneId = Objects.requireNonNull(sceneId);
            return this;
        }

        public Builder transportType(TransportType transportType) {
            this.transportType = Objects.requireNonNull(transportType);
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout);
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = Objects.requireNonNull(readTimeout);
            return this;
        }

        public YcClientConfig build() {
            return new YcClientConfig(this);
        }
    }
}
