package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.dto.ParameterMatchType;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis-Plus QueryWrapper / UpdateWrapper 参数加密单测
 */
class MpWrapperParamEncryptTest {

    private static final String ENC_PREFIX = "ENC:";

    @BeforeEach
    void setUp() {
        FieldCryptoServiceHolder.set(new FieldCryptoService() {
            @Override
            public boolean needEncrypt(String table, String column, String datasourceId) {
                return "user".equalsIgnoreCase(table)
                        && ("phone".equalsIgnoreCase(column) || "name".equalsIgnoreCase(column));
            }

            @Override
            public String encrypt(String table, String column, String plain, String datasourceId) {
                return plain == null ? null : ENC_PREFIX + plain;
            }

            @Override
            public String decrypt(String table, String column, String cipher, String datasourceId) {
                if (cipher != null && cipher.startsWith(ENC_PREFIX)) {
                    return cipher.substring(ENC_PREFIX.length());
                }
                return cipher;
            }
        });
    }

    @AfterEach
    void tearDown() {
        FieldCryptoServiceHolder.reset();
    }

    @Test
    void encryptQueryWrapperEq() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", "13800138000");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Constants.WRAPPER, wrapper);

        String pairKey = firstPairKey(wrapper);
        BoundSql boundSql = boundSql("SELECT * FROM user WHERE (phone = ?)",
                Collections.singletonList("ew.paramNameValuePairs." + pairKey),
                paramMap);

        Map<String, Object> originals = ParameterEncryptHelper.encryptParameters(
                paramMap, boundSql, parseResult(col("user", "phone", 1, ParameterMatchType.EQUAL)), "default");

        assertEquals(ENC_PREFIX + "13800138000", wrapper.getParamNameValuePairs().get(pairKey));
        assertEquals(1, originals.size());
        assertEquals("13800138000", originals.get("ew.paramNameValuePairs." + pairKey));

        ParameterEncryptHelper.restoreParameters(paramMap, boundSql, originals);
        assertEquals("13800138000", wrapper.getParamNameValuePairs().get(pairKey));
        assertFalse(ParameterEncryptHelper.hasBoundSqlOnlyRewrite(boundSql, originals));
    }

    @Test
    void encryptQueryWrapperIn() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        wrapper.in("phone", Arrays.asList("111", "222"));

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Constants.WRAPPER, wrapper);

        List<String> keys = new ArrayList<>(wrapper.getParamNameValuePairs().keySet());
        Collections.sort(keys);
        List<String> properties = new ArrayList<>();
        for (String key : keys) {
            properties.add("ew.paramNameValuePairs." + key);
        }

        BoundSql boundSql = boundSql("SELECT * FROM user WHERE (phone IN (?, ?))", properties, paramMap);

        Map<String, ColumnTableDto> indexMap = new LinkedHashMap<>();
        indexMap.put("1", col("user", "phone", 1, ParameterMatchType.EQUAL));
        indexMap.put("2", col("user", "phone", 2, ParameterMatchType.EQUAL));
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult =
                Pair.of(indexMap, Collections.emptyList());

        Map<String, Object> originals = ParameterEncryptHelper.encryptParameters(
                paramMap, boundSql, parseResult, "default");

        assertEquals(2, originals.size());
        for (String key : keys) {
            Object value = wrapper.getParamNameValuePairs().get(key);
            assertTrue(String.valueOf(value).startsWith(ENC_PREFIX), "value should be encrypted: " + value);
        }

        ParameterEncryptHelper.restoreParameters(paramMap, boundSql, originals);
        assertEquals("111", wrapper.getParamNameValuePairs().get(keys.get(0)));
        assertEquals("222", wrapper.getParamNameValuePairs().get(keys.get(1)));
    }

    @Test
    void encryptUpdateWrapperSetAndWhere() {
        UpdateWrapper<?> wrapper = new UpdateWrapper<>();
        wrapper.set("name", "alice").eq("phone", "13800138000");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Constants.WRAPPER, wrapper);

        List<String> keys = orderedPairKeys(wrapper);
        assertEquals(2, keys.size());

        List<String> properties = Arrays.asList(
                "ew.paramNameValuePairs." + keys.get(0),
                "ew.paramNameValuePairs." + keys.get(1));
        BoundSql boundSql = boundSql("UPDATE user SET name = ? WHERE (phone = ?)", properties, paramMap);

        Map<String, ColumnTableDto> indexMap = new LinkedHashMap<>();
        indexMap.put("1", col("user", "name", 1, ParameterMatchType.EQUAL));
        indexMap.put("2", col("user", "phone", 2, ParameterMatchType.EQUAL));

        Map<String, Object> originals = ParameterEncryptHelper.encryptParameters(
                paramMap, boundSql, Pair.of(indexMap, Collections.emptyList()), "default");

        assertEquals(ENC_PREFIX + "alice", wrapper.getParamNameValuePairs().get(keys.get(0)));
        assertEquals(ENC_PREFIX + "13800138000", wrapper.getParamNameValuePairs().get(keys.get(1)));
        assertEquals(2, originals.size());

        ParameterEncryptHelper.restoreParameters(paramMap, boundSql, originals);
        assertEquals("alice", wrapper.getParamNameValuePairs().get(keys.get(0)));
        assertEquals("13800138000", wrapper.getParamNameValuePairs().get(keys.get(1)));
    }

    @Test
    void skipNonEncryptedColumn() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        wrapper.eq("age", "30");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(Constants.WRAPPER, wrapper);

        String pairKey = firstPairKey(wrapper);
        BoundSql boundSql = boundSql("SELECT * FROM user WHERE (age = ?)",
                Collections.singletonList("ew.paramNameValuePairs." + pairKey),
                paramMap);

        Map<String, Object> originals = ParameterEncryptHelper.encryptParameters(
                paramMap, boundSql, parseResult(col("user", "age", 1, ParameterMatchType.EQUAL)), "default");

        assertTrue(originals.isEmpty());
        assertEquals("30", wrapper.getParamNameValuePairs().get(pairKey));
    }

    @Test
    void mpWrapperParamSupportParse() {
        assertTrue(MpWrapperParamSupport.isWrapperParamProperty("ew.paramNameValuePairs.MPGENVAL1"));
        assertFalse(MpWrapperParamSupport.isWrapperParamProperty("et.phone"));

        MpWrapperParamSupport.ParsedProperty parsed =
                MpWrapperParamSupport.parse("ew.paramNameValuePairs.MPGENVAL2");
        assertEquals("ew", parsed.wrapperPath);
        assertEquals("MPGENVAL2", parsed.pairKey);
    }

    private static String firstPairKey(QueryWrapper<?> wrapper) {
        return wrapper.getParamNameValuePairs().keySet().iterator().next();
    }

    private static List<String> orderedPairKeys(UpdateWrapper<?> wrapper) {
        List<String> keys = new ArrayList<>(wrapper.getParamNameValuePairs().keySet());
        // MPGENVAL 按数字后缀排序，保证与 SQL 占位符顺序一致
        keys.sort((a, b) -> {
            int na = Integer.parseInt(a.replace("MPGENVAL", ""));
            int nb = Integer.parseInt(b.replace("MPGENVAL", ""));
            return Integer.compare(na, nb);
        });
        return keys;
    }

    private static BoundSql boundSql(String sql, List<String> properties, Object parameter) {
        Configuration configuration = new Configuration();
        List<ParameterMapping> mappings = new ArrayList<>();
        for (String property : properties) {
            mappings.add(new ParameterMapping.Builder(configuration, property, Object.class).build());
        }
        StaticSqlSource sqlSource = new StaticSqlSource(configuration, sql, mappings);
        return sqlSource.getBoundSql(parameter);
    }

    private static Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult(ColumnTableDto dto) {
        Map<String, ColumnTableDto> map = new LinkedHashMap<>();
        map.put(String.valueOf(dto.getInsertFieldIndex()), dto);
        return Pair.of(map, Collections.emptyList());
    }

    private static ColumnTableDto col(String table, String column, int index, ParameterMatchType matchType) {
        return ColumnTableDto.builder()
                .sourceTableName(table)
                .sourceColumn(column)
                .insertFieldIndex(index)
                .fromSourceTable(true)
                .matchType(matchType)
                .build();
    }
}
