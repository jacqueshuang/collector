package com.j.soul.yc.captcha.aliyun;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceTokenProviderTest {

    @Test
    void obtain_setsSessionAndHasWebShape() {
        DeviceTokenProvider p = new DeviceTokenProvider();
        AliyunSession s = new AliyunSession("cid", "dc", "1pn9314j", "1uu8u2");
        String token = p.obtain(null, s);
        assertEquals(token, s.deviceToken());
        byte[] raw = Base64.getDecoder().decode(token);
        String plain = new String(raw, StandardCharsets.UTF_8);
        assertTrue(plain.startsWith("WEB#ab034ec0643f91399eb33e062dc7fae1-h-"));
        assertTrue(plain.contains("#"));
        String[] parts = plain.split("#", 3);
        assertEquals(3, parts.length);
        assertTrue(parts[1].contains("-h-"));
        assertTrue(parts[2].length() > 100);
    }
}
