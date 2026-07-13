package com.j.soul.yc.captcha.aliyun;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaVerifyParamBuilderTest {

    @Test
    void captchaParam_isBase64Json() {
        String p = CaptchaVerifyParamBuilder.build("cid", "1pn9314j", "tok");
        String json = new String(Base64.getDecoder().decode(p), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"isSign\":true"));
        assertTrue(json.contains("\"certifyId\":\"cid\""));
        assertTrue(json.contains("\"sceneId\":\"1pn9314j\""));
        assertTrue(json.contains("\"securityToken\":\"tok\""));
    }

    @Test
    void captchaParam_matchesCompactJsonVector() {
        String p = CaptchaVerifyParamBuilder.build("cid", "1pn9314j", "tok");
        assertEquals(
                "eyJjZXJ0aWZ5SWQiOiJjaWQiLCJzY2VuZUlkIjoiMXBuOTMxNGoiLCJpc1NpZ24iOnRydWUsInNlY3VyaXR5VG9rZW4iOiJ0b2sifQ==",
                p);
    }
}
