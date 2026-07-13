package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.config.YcClientConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryGeneratorTest {

    @Test
    void deflateToBase64_roundTrip() throws Exception {
        String plain = "2ee39fd8c18029189e1087465392c165{\"TrackList\":{\"mc\":\"1,2,3, ,0\"}}";
        String b64 = TrajectoryGenerator.deflateToBase64(plain.getBytes(StandardCharsets.UTF_8));
        assertTrue(b64.startsWith("eJ") || b64.startsWith("eJzt") || b64.length() > 8);
        byte[] raw = Base64.getDecoder().decode(b64);
        assertEquals(0x78, raw[0] & 0xff);
        Inflater inf = new Inflater();
        inf.setInput(raw);
        byte[] out = new byte[plain.length() + 64];
        int n = inf.inflate(out);
        inf.end();
        assertEquals(plain, new String(out, 0, n, StandardCharsets.UTF_8));
    }

    @Test
    void transform_fixedDeflateB64_isDeterministic() {
        String sample = "eJztlkFrlEEMhv+KfOdVZiaZTFLowXrw";
        JsVmpTransform t = JsVmpTransform.getDefault();
        byte[] a = t.transform(sample);
        byte[] b = t.transform(sample);
        assertEquals(a.length, sample.length());
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i]);
        }
    }

    @Test
    void buildPlaintext_hasTrackListShape() {
        TrajectoryGenerator gen = new TrajectoryGenerator();
        String plain = gen.buildPlaintext();
        assertTrue(plain.length() > 40);
        assertTrue(plain.contains("\"TrackList\""));
        assertTrue(plain.contains("\"TrackStartTime\""));
        // 32-hex prefix
        assertTrue(plain.substring(0, 32).matches("[0-9a-f]{32}"));
    }

    @Test
    void generate_nullSession_throws() {
        assertThrows(com.j.soul.yc.exception.YcException.class,
                () -> new TrajectoryGenerator().generate(null));
    }

    @Test
    void generator_wiresSharedRuntime_whenReconPresent() {
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
            TrajectoryGenerator gen = new TrajectoryGenerator(cfg, rt);
            assertTrue(rt.hasInit());
            assertFalse(gen.buildPlaintext().isBlank());
        }
    }
}
