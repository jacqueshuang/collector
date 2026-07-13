package com.j.soul.yc.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class GwSigner {

    private GwSigner() {
    }

    public static Map<String, String> sign(
            String path,
            String method,
            String gwKey,
            String gwClient,
            String accessTokenOrNull,
            Long fixedTimestampMsOrNull,
            String fixedNonceOrNull) {
        String normalized = normalizePath(path);
        String upperMethod = method == null ? "" : method.toUpperCase();
        long ts = fixedTimestampMsOrNull != null ? fixedTimestampMsOrNull : System.currentTimeMillis();
        String nonce = fixedNonceOrNull != null ? fixedNonceOrNull : UUID.randomUUID().toString();

        StringBuilder sb = new StringBuilder(160);
        sb.append("path=").append(normalized)
                .append("&method=").append(upperMethod)
                .append("&nonce=").append(nonce)
                .append("&timestamp=").append(ts);
        if (accessTokenOrNull != null && accessTokenOrNull.length() > 7) {
            sb.append("&accessToken=").append(accessTokenOrNull);
        }
        sb.append("&key=").append(gwKey);

        String signature = md5Hex(sb.toString());

        Map<String, String> headers = new LinkedHashMap<>(4);
        headers.put("GW-Timestamp", Long.toString(ts));
        headers.put("GW-Nonce", nonce);
        headers.put("GW-Signature", signature);
        headers.put("GW-Client", gwClient);
        return headers;
    }

    private static String normalizePath(String path) {
        String p = path == null ? "" : path;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return p;
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
