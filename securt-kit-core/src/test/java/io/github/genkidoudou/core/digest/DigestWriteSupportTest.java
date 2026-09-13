package io.github.genkidoudou.core.digest;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.interceptor.SimpleInterceptorPreparedStatement;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
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
                values,
                new DigestRewriteResult(
                        "INSERT INTO user_account(phone, id_card, row_digest) VALUES (?, ?, ?)",
                        Collections.singletonList(3),
                        Collections.singletonList("row_digest"),
                        true,
                        null),
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
    void bindsOnlyTheDigestTargetActuallyAppendedByRewrite() throws Exception {
        DigestConfigRegistry.clear();
        DigestConfigRegistry.register(DATASOURCE, TABLE, Arrays.asList(
                rule(Collections.singletonList("phone"), "phone_digest"),
                rule(Collections.singletonList("id_card"), "id_card_digest")));
        String originalSql =
                "INSERT INTO user_account(phone, phone_digest, id_card) VALUES (?, ?, ?)";
        DigestRewriteResult rewrite = DigestWriteSupport.rewriteForConfiguredDigests(
                originalSql, Collections.singleton(TABLE), DATASOURCE);
        Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();
        values.put(1, "13800138000");
        values.put(2, "caller-supplied-digest");
        values.put(3, "110101199001011234");
        Map<Integer, String> bound = new LinkedHashMap<Integer, String>();

        DigestWriteSupport.applyDigestsBeforeEncrypt(
                rewrite.getSql(),
                Collections.singleton(TABLE),
                DATASOURCE,
                pair(
                        column(1, "phone"),
                        column(2, "phone_digest"),
                        column(3, "id_card")),
                values,
                values,
                rewrite,
                recordingStatement(bound));

        Map<String, String> phone = Collections.singletonMap("phone", "13800138000");
        Map<String, String> idCard =
                Collections.singletonMap("id_card", "110101199001011234");
        assertEquals(strategy.digest(phone), bound.get(2));
        assertEquals(strategy.digest(idCard), bound.get(4));
    }

    @Test
    void reloadWhereUsesStoredValueAndFallsBackToPlainValue() {
        Map<Integer, Object> plain = new LinkedHashMap<Integer, Object>();
        plain.put(2, "plain-id");
        plain.put(3, "plain-tenant");
        Map<Integer, Object> stored = new LinkedHashMap<Integer, Object>();
        stored.put(2, "encrypted-id");

        List<Object> whereParams = DigestWriteSupport.reloadWhereParameters(
                "UPDATE user_account SET phone = ? WHERE id = ? AND tenant = ?",
                plain,
                stored);

        assertEquals(Arrays.<Object>asList("encrypted-id", "plain-tenant"), whereParams);
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

    private ResolvedDigestRule rule(List<String> sources, String target) {
        return new ResolvedDigestRule(
                TABLE,
                sources,
                target,
                HmacSha256DigestStrategy.class,
                FieldEncryptorProperties.PartialUpdate.FAIL,
                false,
                FieldEncryptorProperties.FailurePolicy.FALLBACK);
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
