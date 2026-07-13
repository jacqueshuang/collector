package com.j.soul.yc.config;

import com.j.soul.yc.http.TransportType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YcClientConfigTest {

    @Test
    void defaults_matchProductionPython() {
        YcClientConfig c = YcClientConfig.builder().build();
        assertEquals("https://uc.perfect99.com/api", c.baseUrl());
        assertEquals("8294e640299aae744184b3a529cd1e2f", c.gwKey());
        assertEquals(TransportType.OKHTTP, c.transportType());
        assertEquals("1pn9314j", c.sceneId());
        assertEquals("htmlunit", c.captchaEngine());
    }
}
