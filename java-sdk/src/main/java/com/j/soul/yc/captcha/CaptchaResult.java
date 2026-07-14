package com.j.soul.yc.captcha;

/**
 * 阿里云滑块通过后的结果。
 *
 * @param captchaVerifyParam 发短信用的验证参数（Base64 JSON）
 * @param sceneId            场景 ID
 * @param certifyId          会话 certifyId
 * @param securityToken      Verify 成功后的 securityToken
 */
public record CaptchaResult(
        String captchaVerifyParam,
        String sceneId,
        String certifyId,
        String securityToken) {
}
