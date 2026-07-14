package com.j.soul.yc.model;

/**
 * 发短信接口 {@code /mobile/login/getSmsCodeNewV2} 的 {@code pathType}。
 * <ul>
 *   <li>{@link #RESET_PHONE} (11) — 重置手机号 / 会员卡流程（{@code isShow=resetPhone}）</li>
 *   <li>{@link #LOGIN} (5) — 登录页短信验证码（{@code isShow=login}）</li>
 * </ul>
 */
public enum SmsPathType {
    /** 重置手机号短信（默认产品流）。 */
    RESET_PHONE(11),
    /** 登录页短信验证码。 */
    LOGIN(5);

    private final int value;

    SmsPathType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
