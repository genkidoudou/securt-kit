package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.digest.DigestReadSupport;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.visitor.fieldparse.SelectExpressionColumnSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MyBatis 结果解密助手
 * <p>
 * 依据 core 的 SQL 解析结果（{@code Pair.getValue()} 中的 {@link FieldEncryptorInfoDto}）
 * 对映射结果进行解密，覆盖三类返回值：
 * </p>
 * <ul>
 *   <li>实体（含 List&lt;实体&gt;）：按列名 / 驼峰属性名定位 setter（P0）</li>
 *   <li>Map（含 List&lt;Map&gt;）：按列名（忽略大小写）与驼峰 key 定位（P1）</li>
 *   <li>标量与 JDK 内置类型：跳过，避免误改</li>
 * </ul>
 * <p>
 * 解密统一走 {@code FieldCryptoService} 门面，失败按配置的失败策略降级（迁移期兼容明文）。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public final class ResultDecryptHelper {

    private ResultDecryptHelper() {
    }

    /**
     * 解密查询结果
     *
     * @param result       Executor 返回的结果对象，通常是 {@code List}
     * @param parseResult  core 的 SQL 解析结果（value 为需要解密的字段清单）
     * @param datasourceId 数据源标识，为空按默认数据源处理
     */
    public static void decryptResult(Object result,
                                    Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
                                    String datasourceId) {
        decryptResult(result, parseResult, Collections.<String>emptySet(), datasourceId);
    }

    /**
     * 解密查询结果后，使用同一行中的摘要目标列校验明文源字段。
     */
    public static void decryptResult(
            Object result,
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
            Set<String> tables,
            String datasourceId) {
        if (result == null || parseResult == null) {
            return;
        }
        List<FieldEncryptorInfoDto> fields = parseResult.getValue();
        String dsId = StrUtil.isBlank(datasourceId) ? DatasourceIdResolver.DEFAULT_DATASOURCE_ID : datasourceId;
        List<FieldEncryptorInfoDto> safeFields = fields == null
                ? Collections.<FieldEncryptorInfoDto>emptyList() : fields;
        Map<String, FieldEncryptorInfoDto> nameToField = buildNameIndex(safeFields);
        if (!nameToField.isEmpty()) {
            decryptObject(result, safeFields, nameToField, dsId);
        }
        verifyDigest(result, safeFields, tables, dsId);
    }

    /**
     * 下划线转驼峰
     * <p>
     * 不含下划线时原样返回（{@code phone} → {@code phone}、{@code userName} → {@code userName}），
     * 含下划线时按 MyBatis {@code mapUnderscoreToCamelCase} 的习惯转换（{@code user_name} → {@code userName}）。
     * </p>
     *
     * @param name 列名，可为 null
     * @return 驼峰形式的属性名
     */
    public static String underlineToCamel(String name) {
        if (name == null || name.indexOf('_') < 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length());
        boolean upperNext = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void decryptObject(Object target, List<FieldEncryptorInfoDto> fields,
                                      Map<String, FieldEncryptorInfoDto> nameToField, String datasourceId) {
        if (target == null || isSkippableType(target)) {
            return;
        }
        if (target instanceof Collection) {
            for (Object element : (Collection<Object>) target) {
                decryptObject(element, fields, nameToField, datasourceId);
            }
            return;
        }
        if (target instanceof Map) {
            decryptMap((Map<Object, Object>) target, nameToField, datasourceId);
            return;
        }
        decryptEntity(target, fields, datasourceId);
    }

    @SuppressWarnings("unchecked")
    private static void verifyDigest(Object target, List<FieldEncryptorInfoDto> fields,
                                     Set<String> tables, String datasourceId) {
        if (target == null || tables == null || tables.isEmpty() || isSkippableType(target)) {
            return;
        }
        if (target instanceof Collection) {
            for (Object element : (Collection<Object>) target) {
                verifyDigest(element, fields, tables, datasourceId);
            }
            return;
        }

        Set<String> required = DigestReadSupport.requiredColumns(tables, datasourceId);
        if (required.isEmpty()) {
            return;
        }
        Map<String, String> values = target instanceof Map
                ? digestValuesFromMap((Map<Object, Object>) target, fields, required)
                : digestValuesFromEntity(target, fields, required);
        DigestReadSupport.verifyResultRow(tables, datasourceId, values);
    }

    private static Map<String, String> digestValuesFromMap(
            Map<Object, Object> row, List<FieldEncryptorInfoDto> fields, Set<String> required) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String column : required) {
            ValueLookup lookup = findMapValue(row, candidateNames(column, fields));
            if (lookup.found) {
                values.put(column, lookup.value == null ? null : String.valueOf(lookup.value));
            }
        }
        return values;
    }

    private static Map<String, String> digestValuesFromEntity(
            Object entity, List<FieldEncryptorInfoDto> fields, Set<String> required) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        MetaObject metaObject;
        try {
            metaObject = SystemMetaObject.forObject(entity);
        } catch (Exception e) {
            return values;
        }
        for (String column : required) {
            for (String property : candidateNames(column, fields)) {
                if (!metaObject.hasGetter(property)) {
                    continue;
                }
                try {
                    Object value = metaObject.getValue(property);
                    values.put(column, value == null ? null : String.valueOf(value));
                    break;
                } catch (Exception e) {
                    log.debug("Failed to read digest result property [property={}]: {}",
                            property, e.getMessage());
                }
            }
        }
        return values;
    }

    private static Set<String> candidateNames(String column, List<FieldEncryptorInfoDto> fields) {
        Set<String> candidates = new LinkedHashSet<String>();
        addCandidate(candidates, column);
        addCandidate(candidates, underlineToCamel(column));
        for (FieldEncryptorInfoDto field : fields) {
            if (field != null && column.equalsIgnoreCase(field.getSourceColumn())) {
                candidates.addAll(nameCandidates(field));
            }
        }
        return candidates;
    }

    private static ValueLookup findMapValue(Map<Object, Object> row, Set<String> candidates) {
        for (Map.Entry<Object, Object> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (candidate.equalsIgnoreCase(entry.getKey().toString())) {
                    return new ValueLookup(true, entry.getValue());
                }
            }
        }
        return ValueLookup.NOT_FOUND;
    }

    /**
     * Map 结果解密：按列名 / 源列名 / 驼峰 key 匹配（忽略大小写）
     */
    private static void decryptMap(Map<Object, Object> map, Map<String, FieldEncryptorInfoDto> nameToField,
                                   String datasourceId) {
        if (map.isEmpty()) {
            return;
        }
        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !(value instanceof String)) {
                continue;
            }
            FieldEncryptorInfoDto field = lookupField(nameToField, key.toString());
            if (field == null) {
                continue;
            }
            String decrypted = decrypt(cryptoService, field, (String) value, datasourceId);
            if (decrypted != null && !decrypted.equals(value)) {
                entry.setValue(decrypted);
            }
        }
    }

    /**
     * 实体结果解密：按列名、源列名及其驼峰形式尝试定位属性
     */
    private static void decryptEntity(Object entity, List<FieldEncryptorInfoDto> fields, String datasourceId) {
        MetaObject metaObject;
        try {
            metaObject = SystemMetaObject.forObject(entity);
        } catch (Exception e) {
            log.debug("Failed to create MetaObject for result [type={}]: {}",
                    entity.getClass().getName(), e.getMessage());
            return;
        }

        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
        for (FieldEncryptorInfoDto field : fields) {
            // 候选属性名：列名 / 源列名及其驼峰形式（MyBatis 反射按大小写敏感匹配）
            for (String property : nameCandidates(field)) {
                if (!metaObject.hasGetter(property) || !metaObject.hasSetter(property)) {
                    continue;
                }
                Object value;
                try {
                    value = metaObject.getValue(property);
                } catch (Exception e) {
                    log.debug("Failed to read result property [property={}]: {}", property, e.getMessage());
                    continue;
                }
                if (!(value instanceof String)) {
                    // 属性存在但不是字符串，无需继续尝试其他候选名
                    break;
                }
                String decrypted = decrypt(cryptoService, field, (String) value, datasourceId);
                if (decrypted != null && !decrypted.equals(value)) {
                    try {
                        metaObject.setValue(property, decrypted);
                    } catch (Exception e) {
                        log.debug("Failed to write decrypted value [property={}]: {}", property, e.getMessage());
                    }
                }
                break;
            }
        }
    }

    private static String decrypt(FieldCryptoService cryptoService, FieldEncryptorInfoDto field,
                                  String cipher, String datasourceId) {
        try {
            return cryptoService.decrypt(field.getSourceTableName(), field.getSourceColumn(), cipher, datasourceId);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to decrypt result field [table={}, column={}], using original value: {}",
                        field.getSourceTableName(), field.getSourceColumn(), e.getMessage());
            }
            return cipher;
        }
    }

    /**
     * 构建「列名（规范化） → 字段信息」索引，覆盖别名、源列名及其驼峰形式
     */
    private static Map<String, FieldEncryptorInfoDto> buildNameIndex(List<FieldEncryptorInfoDto> fields) {
        Map<String, FieldEncryptorInfoDto> index = new HashMap<>();
        for (FieldEncryptorInfoDto field : fields) {
            if (field == null || StrUtil.isBlank(field.getSourceTableName())
                    || StrUtil.isBlank(field.getSourceColumn())) {
                continue;
            }
            for (String candidate : nameCandidates(field)) {
                index.putIfAbsent(normalize(candidate), field);
                String compact = SelectExpressionColumnSupport.stripWhitespaceLower(candidate);
                if (compact != null && !compact.isEmpty()) {
                    index.putIfAbsent(compact, field);
                }
            }
        }
        return index;
    }

    private static Set<String> nameCandidates(FieldEncryptorInfoDto field) {
        Set<String> candidates = new LinkedHashSet<>(8);
        addCandidate(candidates, field.getColumnName());
        addCandidate(candidates, underlineToCamel(field.getColumnName()));
        addCandidate(candidates, field.getSourceColumn());
        addCandidate(candidates, underlineToCamel(field.getSourceColumn()));
        String compact = SelectExpressionColumnSupport.stripWhitespaceLower(field.getColumnName());
        addCandidate(candidates, compact);
        String spaced = SelectExpressionColumnSupport.normalizeLabel(field.getColumnName());
        addCandidate(candidates, spaced);
        return candidates;
    }

    private static void addCandidate(Set<String> candidates, String name) {
        if (StrUtil.isNotBlank(name)) {
            candidates.add(name.trim());
        }
    }

    private static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        String compact = SelectExpressionColumnSupport.stripWhitespaceLower(name);
        // buildNameIndex stores both; lookup tries exact normalize then compact
        return trimmed;
    }

    private static FieldEncryptorInfoDto lookupField(Map<String, FieldEncryptorInfoDto> nameToField, String key) {
        if (nameToField == null || key == null) {
            return null;
        }
        FieldEncryptorInfoDto field = nameToField.get(normalize(key));
        if (field != null) {
            return field;
        }
        String compact = SelectExpressionColumnSupport.stripWhitespaceLower(key);
        if (compact != null) {
            field = nameToField.get(compact);
            if (field != null) {
                return field;
            }
            // also keys stored via normalize() of compact
            field = nameToField.get(compact.toLowerCase(Locale.ROOT));
        }
        return field;
    }

    /**
     * 判断是否为无需遍历的类型
     * <p>
     * 标量、日期、枚举、数组等不承载映射结果；JDK 内置类型（{@code java.*} / {@code javax.*}）
     * 中除 Collection / Map 外一律跳过，避免对 MyBatis 之外的对象做反射改写。
     * </p>
     */
    private static boolean isSkippableType(Object target) {
        if (target instanceof Collection || target instanceof Map) {
            return false;
        }
        Class<?> clazz = target.getClass();
        if (clazz.isArray() || clazz.isEnum() || clazz.isPrimitive()) {
            return true;
        }
        String name = clazz.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.");
    }

    private static final class ValueLookup {
        private static final ValueLookup NOT_FOUND = new ValueLookup(false, null);

        private final boolean found;
        private final Object value;

        private ValueLookup(boolean found, Object value) {
            this.found = found;
            this.value = value;
        }
    }
}
