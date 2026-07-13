package com.j.soul.yc.captcha.aliyun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic JSVMP char-position transform: {@code (charCode, index) -> byte}.
 * Table captured offline from dynamic SG of a fixed captcha JS version.
 */
public final class JsVmpTransform {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsVmpTransform DEFAULT = loadDefault();

    private final Map<String, Integer> table;

    public JsVmpTransform(Map<String, Integer> table) {
        this.table = Map.copyOf(table);
    }

    public static JsVmpTransform getDefault() {
        return DEFAULT;
    }

    public int size() {
        return table.size();
    }

    public byte[] transform(String deflateBase64) {
        if (deflateBase64 == null || deflateBase64.isEmpty()) {
            throw new YcException(YcStep.CAPTCHA, "JsVmpTransform input empty");
        }
        byte[] out = new byte[deflateBase64.length()];
        for (int i = 0; i < deflateBase64.length(); i++) {
            int cc = deflateBase64.charAt(i);
            String key = cc + "_" + i;
            Integer v = table.get(key);
            if (v == null) {
                throw new YcException(YcStep.CAPTCHA,
                        "JsVmpTransform missing key " + key + " (table incomplete for this SG version/length)");
            }
            out[i] = (byte) (v & 0xff);
        }
        return out;
    }

    public String transformToBinaryString(String deflateBase64) {
        byte[] bytes = transform(deflateBase64);
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            sb.append((char) (b & 0xff));
        }
        return sb.toString();
    }

    private static JsVmpTransform loadDefault() {
        try (InputStream in = JsVmpTransform.class.getResourceAsStream("/aliyun/transform_table.json")) {
            if (in == null) {
                throw new IllegalStateException("classpath resource /aliyun/transform_table.json missing");
            }
            Map<String, Integer> raw = MAPPER.readValue(in, new TypeReference<HashMap<String, Integer>>() {});
            return new JsVmpTransform(raw);
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("load transform_table failed", e);
        }
    }
}
