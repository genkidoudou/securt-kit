package io.github.genkidoudou.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigestConfigModelTest {

    @Test
    void globalDigestDefaults() {
        FieldEncryptorProperties p = new FieldEncryptorProperties();
        assertEquals(FieldEncryptorProperties.PartialUpdate.RELOAD, p.getDigestPartialUpdate());
        assertFalse(p.isDigestVerifyOnRead());
        assertNull(p.getDigestFailurePolicy());
    }

    @Test
    void tableCanHoldDigestList() {
        FieldEncryptorProperties.TableConfig t = new FieldEncryptorProperties.TableConfig();
        FieldEncryptorProperties.DigestConfig d = new FieldEncryptorProperties.DigestConfig();
        d.setSourceFields(java.util.Arrays.asList("phone", "id_card"));
        d.setTargetField("row_digest");
        t.setDigest(java.util.Collections.singletonList(d));
        assertEquals(1, t.getDigest().size());
        assertEquals("row_digest", t.getDigest().get(0).getTargetField());
    }
}
