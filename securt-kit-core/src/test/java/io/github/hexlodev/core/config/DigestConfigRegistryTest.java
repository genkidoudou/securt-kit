package io.github.hexlodev.core.config;

import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.digest.ResolvedDigestRule;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestConfigRegistryTest {

    @AfterEach
    void tearDown() {
        ConfigInitializer.reset();
        DigestConfigRegistry.clear();
        StrategyCache.clear();
    }

    @Test
    void rejectsTargetFieldAlsoEncrypted() {
        FieldEncryptorProperties properties = baseProps();
        properties.getTables().get(0).getFields().add(field("ROW_DIGEST"));

        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
    }

    @Test
    void rejectsTargetFieldEncryptedByAnotherConfigForSameTableAndDatasource() {
        FieldEncryptorProperties properties = baseProps();
        FieldEncryptorProperties.TableConfig encryptedFields = new FieldEncryptorProperties.TableConfig();
        encryptedFields.setTableName("USER");
        encryptedFields.setFields(new ArrayList<FieldEncryptorProperties.FieldConfig>(
                Collections.singletonList(field("ROW_DIGEST"))));
        encryptedFields.setDigest(Collections.<FieldEncryptorProperties.DigestConfig>emptyList());
        properties.setTables(Arrays.asList(properties.getTables().get(0), encryptedFields));

        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
    }

    @Test
    void rejectsDuplicateTargetFieldIgnoringCase() {
        FieldEncryptorProperties properties = baseProps();
        FieldEncryptorProperties.DigestConfig duplicate = digest("phone", "ROW_DIGEST");
        properties.getTables().get(0).getDigest().add(duplicate);

        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
    }

    @Test
    void rejectsIncompleteAndUnsupportedDigestRules() {
        FieldEncryptorProperties missingSource = baseProps();
        missingSource.getTables().get(0).getDigest().get(0).setSourceFields(Collections.<String>emptyList());
        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(missingSource));

        ConfigInitializer.reset();
        FieldEncryptorProperties unsupported = baseProps();
        unsupported.getTables().get(0).getDigest().get(0).setStrategy(UnsupportedDigestStrategy.class.getName());
        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(unsupported));
    }

    @Test
    void rejectsBlankSourceFieldEntries() {
        for (String sourceField : Arrays.asList(null, "", " ")) {
            FieldEncryptorProperties properties = baseProps();
            properties.getTables().get(0).getDigest().get(0)
                    .setSourceFields(Collections.singletonList(sourceField));

            assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(properties));
            ConfigInitializer.reset();
        }
    }

    @Test
    void requiresHmacKeyAndRegistersConfiguredHmacStrategy() {
        FieldEncryptorProperties missingKey = baseProps();
        missingKey.setDigestHmacKey(" ");
        assertThrows(RuntimeException.class, () -> ConfigInitializer.initialize(missingKey));

        ConfigInitializer.reset();
        FieldEncryptorProperties configured = baseProps();
        ConfigInitializer.initialize(configured);
        assertTrue(StrategyCache.contains(HmacSha256DigestStrategy.class));
    }

    @Test
    void registersResolvedDigestRulesAndClearsOnReset() {
        FieldEncryptorProperties properties = baseProps();
        properties.setDigestPartialUpdate(FieldEncryptorProperties.PartialUpdate.FAIL);
        properties.setDigestVerifyOnRead(true);
        properties.setDigestFailurePolicy(null);
        properties.setFailurePolicy(FieldEncryptorProperties.FailurePolicy.RETRY);

        ConfigInitializer.initialize(properties);

        assertTrue(DigestConfigRegistry.hasDigest("USER", "default"));
        ResolvedDigestRule rule = DigestConfigRegistry.getRules("user", "default").get(0);
        assertEquals("user", rule.getTableName());
        assertEquals(Collections.singletonList("phone"), rule.getSourceFields());
        assertEquals("row_digest", rule.getTargetField());
        assertEquals(HmacSha256DigestStrategy.class, rule.getStrategyClass());
        assertEquals(FieldEncryptorProperties.PartialUpdate.FAIL, rule.getPartialUpdate());
        assertTrue(rule.isVerifyOnRead());
        assertEquals(FieldEncryptorProperties.FailurePolicy.RETRY, rule.getFailurePolicy());

        ConfigInitializer.reset();
        assertFalse(DigestConfigRegistry.hasDigest("user", "default"));
    }

    @Test
    void itemSettingsOverrideGlobalDigestSettings() {
        FieldEncryptorProperties properties = baseProps();
        FieldEncryptorProperties.DigestConfig digest = properties.getTables().get(0).getDigest().get(0);
        digest.setPartialUpdate(FieldEncryptorProperties.PartialUpdate.SKIP);
        digest.setVerifyOnRead(false);
        digest.setFailurePolicy(FieldEncryptorProperties.FailurePolicy.FAIL_FAST);

        ConfigInitializer.initialize(properties);

        ResolvedDigestRule rule = DigestConfigRegistry.getRules("user", "default").get(0);
        assertEquals(FieldEncryptorProperties.PartialUpdate.SKIP, rule.getPartialUpdate());
        assertFalse(rule.isVerifyOnRead());
        assertEquals(FieldEncryptorProperties.FailurePolicy.FAIL_FAST, rule.getFailurePolicy());
    }

    private FieldEncryptorProperties baseProps() {
        FieldEncryptorProperties properties = new FieldEncryptorProperties();
        properties.setEnable(true);
        properties.setDigestStrategy(HmacSha256DigestStrategy.class.getName());
        properties.setDigestHmacKey("k");

        FieldEncryptorProperties.TableConfig table = new FieldEncryptorProperties.TableConfig();
        table.setTableName("user");
        table.setFields(new ArrayList<FieldEncryptorProperties.FieldConfig>(
                Collections.singletonList(field("phone"))));
        table.setDigest(new ArrayList<FieldEncryptorProperties.DigestConfig>(
                Collections.singletonList(digest("phone", "row_digest"))));
        properties.setTables(Collections.singletonList(table));
        return properties;
    }

    private FieldEncryptorProperties.FieldConfig field(String name) {
        FieldEncryptorProperties.FieldConfig field = new FieldEncryptorProperties.FieldConfig();
        field.setFieldName(name);
        return field;
    }

    private FieldEncryptorProperties.DigestConfig digest(String source, String target) {
        FieldEncryptorProperties.DigestConfig digest = new FieldEncryptorProperties.DigestConfig();
        digest.setSourceFields(Arrays.asList(source));
        digest.setTargetField(target);
        return digest;
    }

    public static class UnsupportedDigestStrategy implements FieldEncryptorStrategy {
        @Override
        public String encryption(String oldValue) {
            return oldValue;
        }

        @Override
        public String decryption(String oldValue) {
            return oldValue;
        }
    }
}
