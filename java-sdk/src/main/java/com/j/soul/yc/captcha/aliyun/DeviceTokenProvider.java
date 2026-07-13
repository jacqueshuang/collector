package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Produces Aliyun captcha {@code deviceToken}.
 * Observed shape (UTF-8 then Base64):
 * {@code WEB#<appKey>-h-<tsMs>-<uuid32>#<feilinFingerprintBlob>}
 * <p>
 * Pure-Java FeiLin fingerprint blob (um/z_um.getToken payload) is not fully reduced;
 * this provider emits a structurally valid token for pipeline wiring. Server-side
 * acceptance may require a real FeiLin blob — see Task 6 report.
 */
public final class DeviceTokenProvider {

    private static final String APP_KEY = "ab034ec0643f91399eb33e062dc7fae1";
    private static final SecureRandom RND = new SecureRandom();

    public String obtain(HttpTransport transport, AliyunSession session) {
        Objects.requireNonNull(session, "session");
        // transport reserved for future remote fingerprint endpoints; unused in pure local path
        if (transport == null) {
            // allow null for pure local generation
        }
        try {
            String token = buildWebToken();
            session.setDeviceToken(token);
            return token;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "deviceToken obtain failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build Base64(WEB#appKey-h-ts-uuid#blob). Blob is random stand-in for FeiLin output.
     */
    public String buildWebToken() {
        long ts = System.currentTimeMillis();
        String id = UUID.randomUUID().toString().replace("-", "");
        String blob = randomBlob(600 + RND.nextInt(80));
        String plain = "WEB#" + APP_KEY + "-h-" + ts + "-" + id + "#" + blob;
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomBlob(int approxBytes) {
        byte[] raw = new byte[approxBytes];
        RND.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
