package com.j.soul.yc.captcha.aliyun;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AliyunSignerTest {

    @Test
    void aliyunSign_matchesPython() {
        // Fixed map verified against aliyun_captcha._sign(params, "testsecret", "POST")
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", "testid");
        params.put("Action", "InitCaptchaV3");
        params.put("Format", "JSON");
        params.put("Language", "cn");
        params.put("Mode", "embed");
        params.put("SceneId", "1pn9314j");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", "11111111-1111-1111-1111-111111111111");
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", "2020-01-01T00:00:00Z");
        params.put("Version", "2023-03-05");
        assertEquals("COw5h650/qDztrwZU/ONESf9V0I=", AliyunSigner.sign(params, "testsecret", "POST"));
    }

    @Test
    void aliyunSign_specialUrlEncode() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", "test+id");
        params.put("Action", "A*B");
        params.put("Note", "hello world");
        params.put("Tilde", "a~b");
        assertEquals("BHa7m7fttG8QtLJKCEo/+9qNgeQ=", AliyunSigner.sign(params, "testsecret", "POST"));
    }

    @Test
    void aliyunSign_stripsExistingSignature() {
        Map<String, String> params = new TreeMap<>();
        params.put("Action", "InitCaptchaV3");
        params.put("Signature", "old");
        String withSig = AliyunSigner.sign(params, "testsecret", "POST");
        params.remove("Signature");
        String without = AliyunSigner.sign(params, "testsecret", "POST");
        assertEquals(without, withSig);
    }
}
