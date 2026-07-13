package com.j.soul.yc.captcha.aliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Base64;

public final class CaptchaVerifyParamBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CaptchaVerifyParamBuilder() {
    }

    public static String build(String certifyId, String sceneId, String securityToken) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("certifyId", certifyId);
            node.put("sceneId", sceneId);
            node.put("isSign", true);
            node.put("securityToken", securityToken);
            byte[] json = MAPPER.writeValueAsBytes(node);
            return Base64.getEncoder().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("CaptchaVerifyParam build failed", e);
        }
    }
}
