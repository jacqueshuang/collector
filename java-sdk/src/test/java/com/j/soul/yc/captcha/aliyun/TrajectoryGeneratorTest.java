package com.j.soul.yc.captcha.aliyun;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void generate_producesNonEmptyBase64() {
        AliyunSession session = new AliyunSession("cid", "dc", "1pn9314j", "1uu8u2");
        TrajectoryGenerator gen = new TrajectoryGenerator();
        String data = gen.generate(session);
        assertFalse(data.isBlank());
        assertTrue(data.matches("^[A-Za-z0-9+/=]+$"));
        Base64.getDecoder().decode(data);
        assertEquals(data, session.trajectoryData());
    }

    @Test
    void generate_nullSession_throws() {
        assertThrows(com.j.soul.yc.exception.YcException.class,
                () -> new TrajectoryGenerator().generate(null));
    }
}
