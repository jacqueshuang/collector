package com.j.soul.yc.model;

/**
 * 换绑手机号请求参数。
 *
 * @param cardNo      会员卡号
 * @param mobile      新手机号
 * @param certNo      证件号（上游字段 certificatesNo）
 * @param smsCode     短信验证码（上游字段 verificationCode）
 * @param accessToken 可选登录 token；非空时带 Bearer 并参与 GW 签名
 */
public record ReplaceMobileRequest(
        String cardNo,
        String mobile,
        String certNo,
        String smsCode,
        String accessToken
) {
}
