package io.github.hexlodev.core.digest;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DigestSqlRewriterTest {

    @Test
    void appendInsertColumn() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "INSERT INTO user (phone, name) VALUES (?, ?)",
                Collections.singletonList("row_digest"));
        assertTrue(r.isRewritten());
        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
        assertEquals(Collections.singletonList(Integer.valueOf(3)), r.getAppendedParameterIndexes());
    }

    @Test
    void appendUpdateSet() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "UPDATE user SET phone = ? WHERE id = ?",
                Collections.singletonList("row_digest"));
        assertTrue(r.isRewritten());
        assertTrue(r.getSql().toLowerCase().contains("row_digest"));
        // 新 ? 在 SET 末尾、WHERE 之前 → 索引 2；WHERE id 顺延为 3
        assertEquals(Collections.singletonList(Integer.valueOf(2)), r.getAppendedParameterIndexes());
    }

    @Test
    void skipMultiTable() {
        DigestRewriteResult r = DigestSqlRewriter.tryAppendTargets(
                "UPDATE user u JOIN t ON u.id=t.id SET u.phone = ?",
                Collections.singletonList("row_digest"));
        assertFalse(r.isRewritten());
        assertNotNull(r.getWarnMessage());
    }
}
