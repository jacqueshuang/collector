package com.j.soul.yc.captcha;

public record CaptchaResult(
        String captchaVerifyParam,
        String sceneId,
        String certifyId,
        String securityToken) {
}
