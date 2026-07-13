package com.j.soul.yc;

import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.captcha.aliyun.CaptchaJsRuntime;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.model.ApiResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Env-gated live acceptance. Default {@code mvn test} skips these (no flags).
 *
 * <ul>
 *   <li>{@code YC_LIVE_CAPTCHA=1} — live HtmlUnit captcha (needs recon dir)</li>
 *   <li>{@code YC_LIVE_SMS=1} + {@code YC_TEST_MOBILE} — full sendSms path</li>
 *   <li>{@code YC_RECON_DIR} optional; else auto-resolve {@code ../recon}</li>
 * </ul>
 */
class LiveSendSmsIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "YC_LIVE_CAPTCHA", matches = "1")
    void live_getCaptchaVerifyParam() {
        Path recon = requireRecon();
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.toString()).build();
        try (YcClient client = YcClient.builder().config(cfg).build()) {
            CaptchaResult r = client.getCaptchaVerifyParam();
            assertNotNull(r);
            assertNotNull(r.captchaVerifyParam());
            assertFalse(r.captchaVerifyParam().isBlank());
            assertNotNull(r.certifyId());
            assertFalse(r.certifyId().isBlank());
            assertNotNull(r.securityToken());
            assertFalse(r.securityToken().isBlank());
            assertTrue(r.sceneId() == null || r.sceneId().equals(cfg.sceneId()) || !r.sceneId().isBlank());
            String json = new String(Base64.getDecoder().decode(r.captchaVerifyParam()), StandardCharsets.UTF_8);
            assertTrue(json.contains(r.certifyId()));
            System.out.println("live captcha ok certifyId=" + r.certifyId()
                    + " sceneId=" + r.sceneId()
                    + " paramLen=" + r.captchaVerifyParam().length());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YC_LIVE_SMS", matches = "1")
    void live_sendSms() {
        String mobile = System.getenv("YC_TEST_MOBILE");
        assumeTrue(mobile != null && !mobile.isBlank(),
                "set YC_TEST_MOBILE to run live sendSms");
        Path recon = requireRecon();
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.toString()).build();
        try (YcClient client = YcClient.builder().config(cfg).build()) {
            // Success criteria: no YcException from captcha/HTTP infra.
            // Business code may reject (rate limit / invalid mobile / etc.).
            ApiResult r = client.sendSms(mobile.trim());
            assertNotNull(r, "ApiResult must parse");
            assertNotNull(r.getCode(), "business code present");
            System.out.println("live sendSms mobile=" + mobile.trim()
                    + " code=" + r.getCode()
                    + " msg=" + r.getMsg()
                    + " data=" + r.getData());
        }
    }

    private static Path requireRecon() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(System.getenv("YC_RECON_DIR"));
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        assumeTrue(recon.isPresent(),
                "recon dir required (YC_RECON_DIR or sibling ../recon with feilin094.js)");
        return recon.get();
    }
}
