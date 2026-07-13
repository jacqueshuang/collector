package com.j.soul.yc.crypto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class CrytoLogin {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false)
            .configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false);

    private CrytoLogin() {
    }

    public static Map<String, String> encrypt(Map<String, ?> params, String rsaPublicKeyBase64) {
        return encrypt(params, rsaPublicKeyBase64, CrytoLogin::randomAesKey);
    }

    static Map<String, String> encrypt(Map<String, ?> params, String rsaPublicKeyBase64, Supplier<String> aesKeySupplier) {
        try {
            String aesKey = aesKeySupplier.get();
            String key = Base64.getEncoder().encodeToString(rsaEncrypt(aesKey.getBytes(StandardCharsets.UTF_8), rsaPublicKeyBase64));
            String json = MAPPER.writeValueAsString(params);
            String data = Base64.getEncoder().encodeToString(aesEncrypt(json.getBytes(StandardCharsets.UTF_8), aesKey));
            Map<String, String> out = new LinkedHashMap<>(2);
            out.put("key", key);
            out.put("data", data);
            return out;
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CRYPTO, "crytoLogin encrypt failed", e);
        }
    }

    private static String randomAesKey() {
        char[] buf = new char[16];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = CHARS.charAt(RANDOM.nextInt(CHARS.length()));
        }
        return new String(buf);
    }

    private static byte[] rsaEncrypt(byte[] plain, String rsaPublicKeyBase64) throws Exception {
        byte[] der = Base64.getDecoder().decode(rsaPublicKeyBase64);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plain);
    }

    private static byte[] aesEncrypt(byte[] plain, String aesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"));
        return cipher.doFinal(plain);
    }
}
