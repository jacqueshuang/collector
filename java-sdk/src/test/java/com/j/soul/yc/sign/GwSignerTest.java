package com.j.soul.yc.sign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GwSignerTest {

    @Test
    void sign_matchesPythonVector() {
        var h = GwSigner.sign(
                "/mobile/loginregister/checkMemberMobileAvailable",
                "GET",
                "8294e640299aae744184b3a529cd1e2f",
                "mall_pc",
                null,
                1700000000000L,
                "11111111-1111-1111-1111-111111111111");
        assertEquals("1700000000000", h.get("GW-Timestamp"));
        assertEquals("11111111-1111-1111-1111-111111111111", h.get("GW-Nonce"));
        assertEquals("8d7581e40587f588141f79aa34ffbad9", h.get("GW-Signature"));
        assertEquals("mall_pc", h.get("GW-Client"));
    }

    @Test
    void sign_stripsQueryAndAddsSlash() {
        var h = GwSigner.sign("mobile/x?y=1", "post", "k", "mall_pc", null, 1L, "n");
        assertEquals("1", h.get("GW-Timestamp"));
        assertEquals("n", h.get("GW-Nonce"));
        assertEquals("mall_pc", h.get("GW-Client"));
        assertEquals("00eb981bf58ea204c3630c17d3df38d3", h.get("GW-Signature"));
    }

    @Test
    void sign_includesAccessTokenWhenLongEnough() {
        var h = GwSigner.sign("/mobile/x", "POST", "k", "mall_pc", "12345678", 1L, "n");
        assertEquals("d3aefc3acd54e77fa30f3971cf5202d8", h.get("GW-Signature"));
    }

    @Test
    void sign_omitsAccessTokenWhenTooShort() {
        var h = GwSigner.sign("/mobile/x", "POST", "k", "mall_pc", "short", 1L, "n");
        assertEquals("00eb981bf58ea204c3630c17d3df38d3", h.get("GW-Signature"));
    }
}
