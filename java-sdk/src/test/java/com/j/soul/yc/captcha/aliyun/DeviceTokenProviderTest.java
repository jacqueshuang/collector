package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceTokenProviderTest {

    @Test
    void obtain_withoutRecon_throwsCaptchaStep() {
        // Force missing recon via impossible path
        YcClientConfig cfg = YcClientConfig.builder()
                .reconDir("/tmp/yc-recon-does-not-exist-" + System.nanoTime())
                .build();
        DeviceTokenProvider p = new DeviceTokenProvider(cfg);
        AliyunSession s = new AliyunSession("cid", "dc", "1pn9314j", "1uu8u2");
        YcException ex = assertThrows(YcException.class, () -> p.obtain(null, s));
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        assertTrue(msg.contains("recon") || msg.contains("device") || msg.contains("not found"),
                () -> "unexpected message: " + ex.getMessage());
    }

    @Test
    void constructor_acceptsSharedRuntimeWiring_whenReconPresent() {
        Optional<Path> recon = CaptchaJsRuntime.resolveReconDir(
                Path.of("..", "..", "recon").toAbsolutePath().normalize().toString());
        if (recon.isEmpty()) {
            recon = CaptchaJsRuntime.resolveReconDir(null);
        }
        if (recon.isEmpty()) {
            return;
        }
        YcClientConfig cfg = YcClientConfig.builder().reconDir(recon.get().toString()).build();
        try (CaptchaJsRuntime rt = CaptchaJsRuntime.open(cfg)) {
            DeviceTokenProvider p = new DeviceTokenProvider(rt);
            // only wiring smoke — live token needs network (YC_LIVE_CAPTCHA=1)
            assertTrue(rt.hasInit());
            assertTrue(p != null);
        }
    }
}
