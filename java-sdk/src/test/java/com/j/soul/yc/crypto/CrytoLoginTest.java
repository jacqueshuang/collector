package com.j.soul.yc.crypto;

import com.j.soul.yc.config.YcClientConfig;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrytoLoginTest {

    private static final String RSA = YcClientConfig.builder().build().rsaPublicKeyBase64();

    @Test
    void encrypt_returnsBase64KeyAndData() {
        var out = CrytoLogin.encrypt(Map.of("mobile", "13800138000", "pathType", 11), RSA);
        assertTrue(out.get("key").matches("^[A-Za-z0-9+/=]+$"));
        assertTrue(out.get("data").matches("^[A-Za-z0-9+/=]+$"));
    }

    @Test
    void encrypt_dataDecryptsWithKnownAesKey_whenInjected() throws Exception {
        String aes = "abcdefghijklmnop";
        var out = CrytoLogin.encrypt(Map.of("a", 1), RSA, () -> aes);

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes.getBytes(StandardCharsets.UTF_8), "AES"));
        byte[] plain = cipher.doFinal(Base64.getDecoder().decode(out.get("data")));
        assertEquals("{\"a\":1}", new String(plain, StandardCharsets.UTF_8));
    }
}
