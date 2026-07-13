package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaJsRuntimeTest {

    @Test
    void resolveReconDir_findsRepoAssetsWhenPresent() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(null);
        // In CI without sibling recon this may be empty; when present, scripts exist.
        recon.ifPresent(p -> {
            assertTrue(Files.isDirectory(p));
            assertTrue(Files.isRegularFile(p.resolve("feilin094.js")));
            assertTrue(Files.isRegularFile(p.resolve("AliyunCaptcha.js")));
        });
    }

    @Test
    void open_loadsScriptsAndExposesInit_whenReconPresent() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(
                Path.of("..", "..", "recon").toAbsolutePath().normalize().toString());
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        if (recon.isEmpty()) {
            return; // offline workspace without assets
        }
        YcClientConfig cfg = YcClientConfig.builder()
                .reconDir(recon.get().toString())
                .build();
        try (CaptchaJsRuntime rt = CaptchaJsRuntime.open(cfg)) {
            assertTrue(rt.hasInit());
            assertNotNull(rt.reconDir());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YC_LIVE_CAPTCHA", matches = "1")
    void live_getDeviceToken_fromUmPath() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(System.getenv("YC_RECON_DIR"));
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        assertTrue(recon.isPresent(), "recon required for live test");
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.get().toString()).build();
        try (CaptchaJsRuntime rt = CaptchaJsRuntime.open(cfg)) {
            String token = rt.getDeviceToken();
            assertNotNull(token);
            assertFalse(token.isBlank());
            // Base64 WEB# shape commonly observed
            assertTrue(token.length() > 80);
        }
    }
}
