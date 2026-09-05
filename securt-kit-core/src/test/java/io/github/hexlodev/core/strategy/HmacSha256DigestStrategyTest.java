package io.github.hexlodev.core.strategy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HmacSha256DigestStrategyTest {

    @Test
    void digestIsDeterministicAndVerifies() {
        HmacSha256DigestStrategy s = new HmacSha256DigestStrategy("test-secret");
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("phone", "13800138000");
        m.put("id_card", "110101199001011234");
        String d1 = s.digest(m);
        String d2 = s.digest(m);
        assertNotNull(d1);
        assertEquals(d1, d2);
        assertTrue(s.verifyDigest(m, d1));
        m.put("phone", "13900139000");
        assertFalse(s.verifyDigest(m, d1));
    }

    @Test
    void orderMatters() {
        HmacSha256DigestStrategy s = new HmacSha256DigestStrategy("test-secret");
        Map<String, String> a = new LinkedHashMap<String, String>();
        a.put("phone", "1");
        a.put("id_card", "2");
        Map<String, String> b = new LinkedHashMap<String, String>();
        b.put("id_card", "2");
        b.put("phone", "1");
        assertNotEquals(s.digest(a), s.digest(b));
    }
}
