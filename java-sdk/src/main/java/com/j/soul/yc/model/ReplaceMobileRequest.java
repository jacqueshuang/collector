package com.j.soul.yc.model;

public record ReplaceMobileRequest(
        String cardNo,
        String mobile,
        String certNo,
        String smsCode,
        String accessToken
) {
}
