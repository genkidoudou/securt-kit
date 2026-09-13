package io.github.genkidoudou.monitor.ops;

import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.dto.PrimaryKeyInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorOpsUnitTest {

    @Test
    void primaryKeyConfigWins() {
        MonitorProperties props = new MonitorProperties();
        props.getTablePrimaryKeys().put("user", "uid");
        PrimaryKeyInfo info = PrimaryKeyResolver.resolve(props, "user", null);
        assertEquals("uid", info.getColumn());
        assertEquals("config", info.getSource());
    }

    @Test
    void primaryKeyManualOverrides() {
        MonitorProperties props = new MonitorProperties();
        props.getTablePrimaryKeys().put("user", "uid");
        PrimaryKeyInfo info = PrimaryKeyResolver.resolve(props, "user", "id");
        assertEquals("id", info.getColumn());
        assertEquals("manual", info.getSource());
    }

    @Test
    void whereRejectsEmptyAndMulti() {
        MonitorProperties props = new MonitorProperties();
        assertNotNull(BatchGuard.validateWhere("", props));
        assertNotNull(BatchGuard.validateWhere("id=1; drop table x", props));
        assertNull(BatchGuard.validateWhere("id = 1", props));
    }

    @Test
    void configuredTableAllowList() {
        FieldEncryptorProperties enc = new FieldEncryptorProperties();
        FieldEncryptorProperties.TableConfig tc = new FieldEncryptorProperties.TableConfig();
        tc.setTableName("user");
        FieldEncryptorProperties.FieldConfig fc = new FieldEncryptorProperties.FieldConfig();
        fc.setFieldName("name");
        tc.setFields(Collections.singletonList(fc));
        enc.setTables(Collections.singletonList(tc));
        assertTrue(BatchGuard.isConfiguredTable(enc, "user"));
        assertFalse(BatchGuard.isConfiguredTable(enc, "secret"));
        assertNotNull(BatchGuard.assertAllowedTable(enc, new MonitorProperties(), "secret"));
    }
}
