package io.github.hexlodev.core.digest;

import cn.hutool.core.lang.Pair;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.config.DigestConfigRegistry;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import io.github.hexlodev.core.interceptor.SimpleInterceptorPreparedStatement;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.HmacSha256DigestStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DigestWriteSupportTest {

    private static final String TABLE = "user_account";
    private static final String DATASOURCE = "test";

    private HmacSha256DigestStrategy strategy;

    @BeforeEach
    void setUp() {
        DigestConfigRegistry.clear();
        StrategyCache.clear();
        strategy = new HmacSha256DigestStrategy("write-support-secret");
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(
                new ResolvedDigestRule(
                        TABLE,
                        Arrays.asList("phone", "id_card"),
                        "row_digest",
                        HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.FAIL,
                        false,
                        FieldEncryptorProperties.FailurePolicy.FALLBACK)));
    }

    @Test
    void computesAndBindsAppendedInsertDigest() throws Exception {
        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
        values.put(1, "13800138000");
        values.put(2, "110101199001011234");
        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();

        DigestWriteSupport.applyDigestsBeforeEncrypt(
                "INSERT INTO user_account(phone, id_card, row_digest) VALUES (?, ?, ?)",
                Collections.singleton(TABLE),
                DATASOURCE,
                pair(
                        column(1, "phone"),
                        column(2, "id_card"),
                        column(3, "row_digest")),
                values,
                new DigestRewriteResult(
                        "INSERT INTO user_account(phone, id_card, row_digest) VALUES (?, ?, ?)",
                        Collections.singletonList(3), true, null),
                recordingStatement(bound));

        Map<String, String> ordered = new LinkedHashMap<String, String>();
        ordered.put("phone", "13800138000");
        ordered.put("id_card", "110101199001011234");
        String expected = strategy.digest(ordered);
        assertEquals(expected, bound.get(3));
        assertEquals(expected, values.get(3));
    }

    @Test
    void overwritesExistingTargetParameterWithoutRewrite() throws Exception {
        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
        values.put(1, "13800138000");
        values.put(2, "caller-supplied-digest");
        values.put(3, "110101199001011234");
        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();

        DigestWriteSupport.applyDigestsBeforeEncrypt(
                "INSERT INTO user_account(phone, row_digest, id_card) VALUES (?, ?, ?)",
                Collections.singleton(TABLE),
                DATASOURCE,
                pair(
                        column(1, "phone"),
                        column(2, "row_digest"),
                        column(3, "id_card")),
                values,
                new DigestRewriteResult(
                        "INSERT INTO user_account(phone, row_digest, id_card) VALUES (?, ?, ?)",
                        Collections.<Integer>emptyList(), false, null),
                recordingStatement(bound));

        Map<String, String> ordered = new LinkedHashMap<String, String>();
        ordered.put("phone", "13800138000");
        ordered.put("id_card", "110101199001011234");
        String expected = strategy.digest(ordered);
        assertEquals(Collections.singletonMap(2, expected), bound);
        assertEquals(expected, values.get(2));
    }

    @Test
    void skipsMultiRowInsertRewriteBecauseAppendedIndexesAreSingleRowOnly() {
        DigestRewriteResult result = DigestWriteSupport.rewriteForConfiguredDigests(
                "INSERT INTO user_account(phone, id_card) VALUES (?, ?), (?, ?)",
                Collections.singleton(TABLE),
                DATASOURCE);

        assertFalse(result.isRewritten());
    }

    @Test
    void shiftsApplicationParameterPastInsertedUpdateDigest() throws Exception {
        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();
        PreparedStatement delegate = recordingStatement(bound);
        SimpleInterceptorPreparedStatement statement = new SimpleInterceptorPreparedStatement(
                delegate,
                "UPDATE user_account SET phone = ?, row_digest = ? WHERE id = ?",
                DATASOURCE);
        statement.setDigestRewriteResult(new DigestRewriteResult(
                "UPDATE user_account SET phone = ?, row_digest = ? WHERE id = ?",
                Collections.singletonList(2), true, null));

        statement.setString(2, "where-id");

        assertEquals("where-id", bound.get(3));
        assertFalse(bound.containsKey(2));
    }

    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair(ColumnTableDto... columns) {
        Map<String, ColumnTableDto> mappings = new LinkedHashMap<String, ColumnTableDto>();
        for (ColumnTableDto column : columns) {
            mappings.put(String.valueOf(column.getInsertFieldIndex()), column);
        }
        return Pair.of(mappings, Collections.<FieldEncryptorInfoDto>emptyList());
    }

    private ColumnTableDto column(int index, String name) {
        return ColumnTableDto.builder()
                .sourceTableName(TABLE)
                .sourceColumn(name)
                .insertFieldIndex(index)
                .fromSourceTable(true)
                .build();
    }

    private PreparedStatement recordingStatement(Map<Integer, String> bound) {
        return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("setString".equals(method.getName())) {
                        bound.put((Integer) args[0], (String) args[1]);
                        return null;
                    }
                    if ("getConnection".equals(method.getName())) {
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
