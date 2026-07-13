package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.captcha.CaptchaResult;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunCaptchaProviderTest {

    @Test
    void mapSolveResult_buildsBase64CaptchaVerifyParam() {
        CaptchaSolveResult solve = new CaptchaSolveResult(
                "cid-1", "dev-tok", "traj-data", "sec-tok", Boolean.TRUE);
        CaptchaResult r = AliyunCaptchaProvider.mapSolveResult(solve, "1pn9314j");
        assertEquals("1pn9314j", r.sceneId());
        assertEquals("cid-1", r.certifyId());
        assertEquals("sec-tok", r.securityToken());
        String json = new String(Base64.getDecoder().decode(r.captchaVerifyParam()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"certifyId\":\"cid-1\""));
        assertTrue(json.contains("\"sceneId\":\"1pn9314j\""));
        assertTrue(json.contains("\"securityToken\":\"sec-tok\""));
        assertTrue(json.contains("\"isSign\":true"));
        assertEquals(CaptchaVerifyParamBuilder.build("cid-1", "1pn9314j", "sec-tok"), r.captchaVerifyParam());
    }

    @Test
    void mapSolveResult_nullVerifyResult_acceptedWhenSecurityTokenPresent() {
        CaptchaSolveResult solve = new CaptchaSolveResult("cid", "dt", "data", "stok", null);
        CaptchaResult r = AliyunCaptchaProvider.mapSolveResult(solve, "1pn9314j");
        assertEquals("stok", r.securityToken());
    }

    @Test
    void mapSolveResult_missingSecurityToken_throwsCaptcha() {
        CaptchaSolveResult solve = new CaptchaSolveResult("cid", "dt", "data", null, Boolean.TRUE);
        YcException ex = assertThrows(YcException.class,
                () -> AliyunCaptchaProvider.mapSolveResult(solve, "1pn9314j"));
        assertEquals(YcStep.CAPTCHA, ex.getStep());
        assertTrue(ex.getMessage().contains("securityToken"));
    }

    @Test
    void mapSolveResult_verifyFalse_throwsCaptcha() {
        CaptchaSolveResult solve = new CaptchaSolveResult("cid", "dt", "data", "stok", Boolean.FALSE);
        YcException ex = assertThrows(YcException.class,
                () -> AliyunCaptchaProvider.mapSolveResult(solve, "1pn9314j"));
        assertEquals(YcStep.CAPTCHA, ex.getStep());
        assertTrue(ex.getMessage().contains("VerifyResult=false"));
    }

    @Test
    void mapSolveResult_missingCertifyId_throwsCaptcha() {
        CaptchaSolveResult solve = new CaptchaSolveResult("  ", "dt", "data", "stok", Boolean.TRUE);
        YcException ex = assertThrows(YcException.class,
                () -> AliyunCaptchaProvider.mapSolveResult(solve, "1pn9314j"));
        assertEquals(YcStep.CAPTCHA, ex.getStep());
    }

    @Test
    void configFromYcClient_exposesPrefixAndScene() {
        YcClientConfig cfg = YcClientConfig.builder()
                .captchaPrefix("1uu8u2")
                .sceneId("1pn9314j")
                .reconDir("/tmp/recon")
                .build();
        AliyunCaptchaConfig c = AliyunCaptchaConfig.from(cfg);
        assertEquals("1uu8u2", c.prefix());
        assertEquals("1pn9314j", c.sceneId());
        assertEquals("/tmp/recon", c.reconDir());
    }

    @Test
    void provider_usesInjectedTransport_andClosesOwnedRuntimeOnly() {
        AtomicInteger closes = new AtomicInteger();
        HttpTransport mockTx = new HttpTransport() {
            @Override
            public HttpResponse execute(HttpRequest request) {
                return new HttpResponse(200, null, "{}".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        YcClientConfig cfg = YcClientConfig.builder().build();
        try (AliyunCaptchaProvider p = new AliyunCaptchaProvider(cfg, mockTx)) {
            assertNotNull(p.transport());
            assertEquals(mockTx, p.transport());
            assertEquals("1uu8u2", p.captchaConfig().prefix());
        }
        // Injected transport must not be closed by provider
        assertEquals(0, closes.get());
    }

    @Test
    void provider_smokeOpenRuntime_whenReconPresent() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(
                Path.of("..", "..", "recon").toAbsolutePath().normalize().toString());
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        if (recon.isEmpty()) {
            return;
        }
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.get().toString()).build();
        try (CaptchaJsRuntime rt = CaptchaJsRuntime.open(cfg);
             AliyunCaptchaProvider p = new AliyunCaptchaProvider(cfg, rt.transport(), rt)) {
            assertTrue(rt.hasInit());
            assertEquals(cfg.sceneId(), p.captchaConfig().sceneId());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YC_LIVE_CAPTCHA", matches = "1")
    void live_getCaptchaVerifyParam_succeeds() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(System.getenv("YC_RECON_DIR"));
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        assertTrue(recon.isPresent(), "recon required for live captcha");
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.get().toString()).build();
        try (AliyunCaptchaProvider p = new AliyunCaptchaProvider(cfg, null)) {
            CaptchaResult r = p.getCaptchaVerifyParam();
            assertNotNull(r.captchaVerifyParam());
            assertFalse(r.captchaVerifyParam().isBlank());
            assertEquals(cfg.sceneId(), r.sceneId());
            assertNotNull(r.certifyId());
            assertFalse(r.certifyId().isBlank());
            assertNotNull(r.securityToken());
            assertFalse(r.securityToken().isBlank());
            String json = new String(Base64.getDecoder().decode(r.captchaVerifyParam()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"isSign\":true"));
            assertTrue(json.contains(r.certifyId()));
        }
    }
}
