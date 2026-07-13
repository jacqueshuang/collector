package com.j.soul.yc.model;

/**
 * {@code pathType} for {@code /mobile/login/getSmsCodeNewV2}.
 * <ul>
 *   <li>{@link #RESET_PHONE} (11) — reset phone / member card flow ({@code isShow=resetPhone})</li>
 *   <li>{@link #LOGIN} (5) — login / password-related SMS on login page ({@code isShow=login})</li>
 * </ul>
 */
public enum SmsPathType {
    /** Reset mobile number SMS (current default product flow). */
    RESET_PHONE(11),
    /** Login page SMS verification code. */
    LOGIN(5);

    private final int value;

    SmsPathType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
