package com.j.soul.yc.captcha.aliyun;

import java.util.Objects;

/** Holds InitCaptchaV3 result fields needed by trajectory / deviceToken / Verify. */
public final class AliyunSession {

    private final String certifyId;
    private final String deviceConfig;
    private final String sceneId;
    private final String prefix;
    private String deviceToken;
    private String trajectoryData;
    private String securityToken;

    public AliyunSession(String certifyId, String deviceConfig, String sceneId, String prefix) {
        this.certifyId = Objects.requireNonNull(certifyId, "certifyId");
        this.deviceConfig = deviceConfig;
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    public String certifyId() {
        return certifyId;
    }

    public String deviceConfig() {
        return deviceConfig;
    }

    public String sceneId() {
        return sceneId;
    }

    public String prefix() {
        return prefix;
    }

    public String deviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public String trajectoryData() {
        return trajectoryData;
    }

    public void setTrajectoryData(String trajectoryData) {
        this.trajectoryData = trajectoryData;
    }

    public String securityToken() {
        return securityToken;
    }

    public void setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
    }
}
