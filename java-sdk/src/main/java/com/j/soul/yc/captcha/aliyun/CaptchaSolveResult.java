package com.j.soul.yc.captcha.aliyun;

/** Material produced by in-process Aliyun SDK (GraalJS). */
public final class CaptchaSolveResult {

    private final String certifyId;
    private final String deviceToken;
    private final String data;
    private final String securityToken;
    private final Boolean verifyResult;

    public CaptchaSolveResult(String certifyId, String deviceToken, String data,
                              String securityToken, Boolean verifyResult) {
        this.certifyId = certifyId;
        this.deviceToken = deviceToken;
        this.data = data;
        this.securityToken = securityToken;
        this.verifyResult = verifyResult;
    }

    public String certifyId() {
        return certifyId;
    }

    public String deviceToken() {
        return deviceToken;
    }

    public String data() {
        return data;
    }

    public String securityToken() {
        return securityToken;
    }

    public Boolean verifyResult() {
        return verifyResult;
    }
}
