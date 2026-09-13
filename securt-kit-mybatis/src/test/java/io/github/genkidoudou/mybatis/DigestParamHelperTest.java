package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.exception.DigestMismatchException;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.strategy.HmacSha256DigestStrategy;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestParamHelperTest {

    private static final String TABLE = "user_account";
    private static final String DATASOURCE = "test";

    private HmacSha256DigestStrategy strategy;

    @BeforeEach
    void setUp() {
        DigestConfigRegistry.clear();
        StrategyCache.clear();
        strategy = new HmacSha256DigestStrategy("mybatis-digest-secret");
        StrategyCache.registerStrategy(HmacSha256DigestStrategy.class, strategy);
        DigestConfigRegistry.register(DATASOURCE, TABLE, Collections.singletonList(
                new ResolvedDigestRule(
                        TABLE,
                        Arrays.asList("phone", "id_card"),
                        "row_digest",
                        HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.FAIL,
                        true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));
    }

    @Test
    void appendsInsertDigestSqlMappingAndAdditionalParameter() {
        Map<String, Object> parameter = new LinkedHashMap<String, Object>();
        parameter.put("phone", "13800138000");
        parameter.put("idCard", "110101199001011234");
        BoundSql boundSql = boundSql(
                "INSERT INTO user_account(phone, id_card) VALUES (?, ?)",
                Arrays.asList("phone", "idCard"),
                parameter);

        boolean rewritten = ParameterEncryptHelper.applyDigests(
                parameter,
                boundSql,
                pair(column(1, "phone"), column(2, "id_card")),
                Collections.singleton(TABLE),
                DATASOURCE);

        Map<String, String> sourceValues = new LinkedHashMap<String, String>();
        sourceValues.put("phone", "13800138000");
        sourceValues.put("id_card", "110101199001011234");
        String expected = strategy.digest(sourceValues);

        assertTrue(rewritten);
        assertTrue(boundSql.getSql().toLowerCase().contains("row_digest"));
        assertEquals(3, boundSql.getParameterMappings().size());
        assertEquals("__securtkit_digest_row_digest",
                boundSql.getParameterMappings().get(2).getProperty());
        assertEquals(expected,
                boundSql.getAdditionalParameter("__securtkit_digest_row_digest"));
    }

    @Test
    void skipsUncomputedDigestTargetWithoutAddingUnboundPlaceholder() {
        DigestConfigRegistry.clear();
        DigestConfigRegistry.register(DATASOURCE, TABLE, Arrays.asList(
                new ResolvedDigestRule(
                        TABLE,
                        Collections.singletonList("phone"),
                        "phone_digest",
                        HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.SKIP,
                        true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST),
                new ResolvedDigestRule(
                        TABLE,
                        Collections.singletonList("id_card"),
                        "id_card_digest",
                        HmacSha256DigestStrategy.class,
                        FieldEncryptorProperties.PartialUpdate.SKIP,
                        true,
                        FieldEncryptorProperties.FailurePolicy.FAIL_FAST)));
        Map<String, Object> parameter = new LinkedHashMap<String, Object>();
        parameter.put("phone", "13800138000");
        parameter.put("id", 1L);
        BoundSql boundSql = boundSql(
                "UPDATE user_account SET phone = ? WHERE id = ?",
                Arrays.asList("phone", "id"),
                parameter);

        boolean rewritten = ParameterEncryptHelper.applyDigests(
                parameter,
                boundSql,
                pair(column(1, "phone"), column(2, "id")),
                Collections.singleton(TABLE),
                DATASOURCE);

        assertTrue(rewritten);
        assertTrue(boundSql.getSql().toLowerCase().contains("phone_digest"));
        assertFalse(boundSql.getSql().toLowerCase().contains("id_card_digest"));
        assertEquals(boundSql.getParameterMappings().size(), countPlaceholders(boundSql.getSql()));
        assertEquals(3, boundSql.getParameterMappings().size());
    }

    @Test
    void verifiesMapDigestAfterResultDecryption() {
        Map<String, String> sourceValues = new LinkedHashMap<String, String>();
        sourceValues.put("phone", "13800138000");
        sourceValues.put("id_card", "110101199001011234");
        Map<String, Object> row = new LinkedHashMap<String, Object>(sourceValues);
        row.put("row_digest", "tampered");

        assertThrows(DigestMismatchException.class, () -> ResultDecryptHelper.decryptResult(
                row,
                Pair.of(Collections.<String, ColumnTableDto>emptyMap(),
                        Collections.<FieldEncryptorInfoDto>emptyList()),
                Collections.singleton(TABLE),
                DATASOURCE));
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static BoundSql boundSql(String sql, List<String> properties, Object parameter) {
        Configuration configuration = new Configuration();
        List<ParameterMapping> mappings = new ArrayList<ParameterMapping>();
        for (String property : properties) {
            mappings.add(new ParameterMapping.Builder(configuration, property, Object.class).build());
        }
        return new StaticSqlSource(configuration, sql, mappings).getBoundSql(parameter);
    }

    private static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair(
            ColumnTableDto... columns) {
        Map<String, ColumnTableDto> mappings = new LinkedHashMap<String, ColumnTableDto>();
        for (ColumnTableDto column : columns) {
            mappings.put(String.valueOf(column.getInsertFieldIndex()), column);
        }
        return Pair.of(mappings, Collections.<FieldEncryptorInfoDto>emptyList());
    }

    private static ColumnTableDto column(int index, String name) {
        return ColumnTableDto.builder()
                .sourceTableName(TABLE)
                .sourceColumn(name)
                .insertFieldIndex(index)
                .fromSourceTable(true)
                .build();
    }
}
