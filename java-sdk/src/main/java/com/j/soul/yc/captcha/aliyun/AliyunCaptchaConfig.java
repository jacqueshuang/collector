package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;

import java.util.Objects;

/** Aliyun captcha scene settings (prefix / scene / recon assets). */
public final class AliyunCaptchaConfig {

    private final String prefix;
    private final String sceneId;
    private final String reconDir;

    public AliyunCaptchaConfig(String prefix, String sceneId, String reconDir) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.reconDir = reconDir;
    }

    public static AliyunCaptchaConfig from(YcClientConfig config) {
        Objects.requireNonNull(config, "config");
        return new AliyunCaptchaConfig(config.captchaPrefix(), config.sceneId(), config.reconDir());
    }

    public String prefix() {
        return prefix;
    }

    public String sceneId() {
        return sceneId;
    }

    public String reconDir() {
        return reconDir;
    }
}
