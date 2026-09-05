package io.github.hexlodev.core.digest;

import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.config.DigestConfigRegistry;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.exception.DigestMismatchException;
import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DigestReadSupportTest {

    private static final String TABLE = "user_account";
    private static final String DATASOURCE = "test";

    @BeforeEach
    void setUp() {
        DigestConfigRegistry.clear();
        StrategyCache.clear();
        StrategyCache.registerStrategy(
                HmacSha256DigestStrategy.class,
                new HmacSha256DigestStrategy("test-secret"));
    }

    @Test
    void fallbackReturnsRowWhenDigestDoesNotMatch() {
        registerRule(FieldEncryptorProperties.FailurePolicy.FALLBACK);

        assertDoesNotThrow(() -> DigestReadSupport.verifyResultRow(
                Collections.singleton(TABLE), DATASOURCE, rowWithDigest("wrong")));
    }

    @Test
    void failFastThrowsWhenDigestDoesNotMatch() {
        registerRule(FieldEncryptorProperties.FailurePolicy.FAIL_FAST);

        assertThrows(DigestMismatchException.class, () -> DigestReadSupport.verifyResultRow(
                Collections.singleton(TABLE), DATASOURCE, rowWithDigest("wrong")));
    }

    private Map<String, String> rowWithDigest(String digest) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("Phone", "13800138000");
        row.put("ID_CARD", "110101199001011234");
        row.put("ROW_DIGEST", digest);
        return row;
    }

    private void registerRule(FieldEncryptorProperties.FailurePolicy failurePolicy) {
        ResolvedDigestRule rule = new ResolvedDigestRule(
                TABLE,
                Arrays.asList("phone", "id_card"),
                "row_digest",
                HmacSha256DigestStrategy.class,
                FieldEncryptorProperties.PartialUpdate.SKIP,
                true,
                failurePolicy);
        DigestConfigRegistry.register(
                DATASOURCE, TABLE, Collections.singletonList(rule));
    }
}
