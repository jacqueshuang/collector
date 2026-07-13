package com.j.soul.yc.captcha.aliyun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsVmpTransformTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void tableLoaded() {
        assertTrue(JsVmpTransform.getDefault().size() > 4000);
    }

    @Test
    void knownVector_prefixMatchesCapturedPair() throws Exception {
        JsonNode v = loadVector();
        String prefix = v.get("deflateB64Prefix").asText();
        byte[] expected = hex(v.get("transformedHexPrefix").asText());
        byte[] actual = JsVmpTransform.getDefault().transform(prefix);
        assertArrayEquals(expected, actual);
    }

    @Test
    void knownVector_fullDeflateProducesCapturedDataB64() throws Exception {
        JsonNode v = loadVector();
        String deflateB64 = v.get("fullDeflateB64").asText();
        String expectedData = v.get("fullDataB64").asText();
        byte[] transformed = JsVmpTransform.getDefault().transform(deflateB64);
        String actual = Base64.getEncoder().encodeToString(transformed);
        assertEquals(expectedData, actual);
    }

    private static JsonNode loadVector() throws Exception {
        try (InputStream in = JsVmpTransformTest.class.getResourceAsStream("/aliyun_transform_vector.json")) {
            return MAPPER.readTree(in);
        }
    }

    private static byte[] hex(String h) {
        int n = h.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
