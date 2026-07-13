package com.j.soul.yc.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesMsgField() throws Exception {
        ApiResult r = mapper.readValue("{\"code\":200,\"msg\":\"ok\",\"data\":null}", ApiResult.class);
        assertEquals(200, r.getCode());
        assertEquals("ok", r.getMsg());
    }

    @Test
    void parsesMessageAlias() throws Exception {
        ApiResult r = mapper.readValue(
                "{\"code\":200,\"message\":\"success\",\"data\":{\"x\":1}}",
                ApiResult.class);
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMsg());
        assertNotNull(r.getData());
        assertEquals(1, r.getData().get("x").asInt());
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        ApiResult r = mapper.readValue(
                "{\"code\":0,\"message\":\"m\",\"extra\":true}",
                ApiResult.class);
        assertEquals(0, r.getCode());
        assertEquals("m", r.getMsg());
    }
}
