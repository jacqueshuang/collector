package com.j.soul.yc.captcha;

/** Produces business-facing captcha verify material for SMS / login flows. */
public interface CaptchaProvider {
    CaptchaResult getCaptchaVerifyParam();
}
