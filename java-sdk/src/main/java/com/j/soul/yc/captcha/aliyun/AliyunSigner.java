package com.j.soul.yc.captcha.aliyun;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class AliyunSigner {

    private AliyunSigner() {
    }

    public static String sign(Map<String, String> params, String keySecret, String method) {
        List<Map.Entry<String, String>> items = new ArrayList<>(params.size());
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!"Signature".equals(e.getKey())) {
                items.add(e);
            }
        }
        items.sort(Map.Entry.comparingByKey());

        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                canonical.append('&');
            }
            Map.Entry<String, String> e = items.get(i);
            canonical.append(percentEncode(e.getKey()))
                    .append('=')
                    .append(percentEncode(e.getValue() == null ? "" : e.getValue()));
        }

        String stringToSign = method + "&%2F&" + percentEncode(canonical.toString());
        return hmacSha1Base64(keySecret + "&", stringToSign);
    }

    static String percentEncode(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() * 3);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else if (c == ' ') {
                out.append("%20");
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    private static String hmacSha1Base64(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Aliyun HMAC-SHA1 failed", e);
        }
    }
}
