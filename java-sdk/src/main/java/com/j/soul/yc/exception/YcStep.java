package com.j.soul.yc.exception;

/**
 * {@link YcException} 失败阶段，便于调用方区分错误来源。
 */
public enum YcStep {
    /** 检查手机号 */
    CHECK_MOBILE,
    /** 阿里云滑块 */
    CAPTCHA,
    /** 发短信（含批量） */
    SMS,
    /** 换绑手机号 */
    REPLACE,
    /** 短信登录提交 */
    LOGIN,
    /** 通用 HTTP / 客户端已关闭 */
    HTTP,
    /** crytoLogin 等加密 */
    CRYPTO,
    /** GW 签名 */
    SIGN
}
