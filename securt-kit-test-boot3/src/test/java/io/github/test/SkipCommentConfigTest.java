package io.github.test;

import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 SQL 注释跳过配置是否生效。
 */
class SkipCommentConfigTest {

    @AfterEach
    void tearDown() {
        ConfigInitializer.reset();
    }

    @Test
    void shouldSkipSqlWhenCommentTokenPresent() {
        FieldEncryptorProperties properties = new FieldEncryptorProperties();
        properties.setEnable(true);
        properties.setTables(Collections.emptyList());

        FieldEncryptorProperties.SkipCommentConfig skipComment = new FieldEncryptorProperties.SkipCommentConfig();
        skipComment.setEnable(true);
        skipComment.setToken("MY_SKIP");
        properties.setSkipComment(skipComment);

        ConfigInitializer.initialize(properties);

        assertTrue(ConfigInitializer.shouldSkipByComment("SELECT 1 /* MY_SKIP */"));
        assertTrue(ConfigInitializer.shouldSkipByComment("SELECT 1 /*MY_SKIP*/"));
        assertFalse(ConfigInitializer.shouldSkipByComment("SELECT 1"));
    }
}


