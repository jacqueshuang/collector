package com.j.soul.yc.captcha.aliyun;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class DeviceDataGenerator {

    private static final byte[] AES_KEY = "c175a358550d02e2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
    private static final String APP_KEY = "ab034ec0643f91399eb33e062dc7fae1";
    private static final String DEVICE_TYPE = "10001";
    private static final String APP_VERSION = "W20220202";
    private static final String PLATFORM = "W";
    private static final String APP_NAME = "saf-captcha";
    private static final String REGION = "cn";

    private DeviceDataGenerator() {
    }

    public static String generate(String prefix, String sceneId) {
        String inner = PLATFORM + "#" + APP_NAME + "#" + sceneId + "#captcha-normal#" + prefix + "#" + REGION;
        String innerEncrypted = aesCbcBase64(inner);
        String outer = APP_KEY + "#" + DEVICE_TYPE + "#" + innerEncrypted + "#" + APP_VERSION + "#CLOUD#";
        return aesCbcBase64(outer);
    }

    private static String aesCbcBase64(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_IV));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("DeviceData AES encrypt failed", e);
        }
    }
}
