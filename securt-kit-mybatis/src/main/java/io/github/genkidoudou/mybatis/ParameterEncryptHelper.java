package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.core.digest.DigestRewriteResult;
import io.github.genkidoudou.core.digest.DigestService;
import io.github.genkidoudou.core.digest.DigestSqlRewriter;
import io.github.genkidoudou.core.digest.ResolvedDigestRule;
import io.github.genkidoudou.core.exception.SecurtKitException;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import io.github.genkidoudou.core.parser.dto.ParameterMatchType;
import io.github.genkidoudou.core.strategy.like.LikeHandleContext;
import io.github.genkidoudou.core.strategy.like.LikeHandleResult;
import io.github.genkidoudou.core.strategy.like.LikePatternHandlerHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MyBatis 参数加密助手
 * <p>
 * 与 JDBC 通道的 {@code ParameterEncryptor} 语义对齐：由 SQL 解析结果得到
 * 「占位符序号 → 表.列」映射，再按 {@link BoundSql#getParameterMappings()} 的顺序
 * （序号从 1 开始，与 JDBC 参数位一致）定位参数属性，对需要加密的字符串值执行加密。
 * </p>
 * <p>
 * 加密采用「就地改写 + 执行后还原」策略：绑定前把明文替换为密文，SQL 执行结束后由
 * {@link #restoreParameters} 还原，避免业务对象上残留密文。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public final class ParameterEncryptHelper {

    private ParameterEncryptHelper() {
    }

    /**
     * 使用参数明文计算摘要，并将摘要参数接入当前 BoundSql。
     *
     * @return true 表示 BoundSql 的 SQL 或参数映射已改写，调用方需保证执行同一实例
     */
    public static boolean applyDigests(
            Object parameter,
            BoundSql boundSql,
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
            Set<String> tables,
            String datasourceId) {
        return applyDigests(parameter, boundSql, parseResult, tables, datasourceId, null);
    }

    static boolean applyDigests(
            Object parameter,
            BoundSql boundSql,
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
            Set<String> tables,
            String datasourceId,
            Connection reloadConnection) {
        if (boundSql == null || StrUtil.isBlank(boundSql.getSql())
                || !isSupportedWrite(boundSql.getSql())) {
            return false;
        }
        String dsId = normalizeDatasourceId(datasourceId);
        String table = singleDigestTable(tables, dsId);
        if (table == null) {
            return false;
        }

        List<ResolvedDigestRule> rules = DigestConfigRegistry.getRules(table, dsId);
        List<String> targets = new ArrayList<String>(rules.size());
        for (ResolvedDigestRule rule : rules) {
            targets.add(rule.getTargetField());
        }
        if (isMultiRowInsert(boundSql.getSql())) {
            log.warn("【securt-kit】Multi-row INSERT is not supported for MyBatis digest rewrite");
            return false;
        }

        List<ParameterMapping> originalMappings = boundSql.getParameterMappings();
        Map<Integer, ColumnTableDto> columnsByIndex = buildParameterIndexMap(parseResult);
        MetaObject parameterMetaObject = parameter == null ? null : SystemMetaObject.forObject(parameter);
        Map<String, String> availablePlain = new LinkedHashMap<String, String>();
        Map<Integer, Object> valuesByIndex = new LinkedHashMap<Integer, Object>();
        for (int i = 0; originalMappings != null && i < originalMappings.size(); i++) {
            int index = i + 1;
            Object value = isScalarStringParameter(parameter)
                    ? parameter
                    : readValue(parameter, parameterMetaObject, boundSql,
                    originalMappings.get(i).getProperty());
            valuesByIndex.put(index, value);
            ColumnTableDto column = columnsByIndex.get(index);
            if (column != null && equalsIgnoreCase(table, column.getSourceTableName())) {
                availablePlain.put(column.getSourceColumn(),
                        value == null ? null : String.valueOf(value));
            }
        }

        boolean insert = isInsert(boundSql.getSql());
        ReloadContext reload = buildReloadContext(
                boundSql.getSql(), valuesByIndex, columnsByIndex, dsId);
        Map<String, String> digests = new DigestService().computeTargetDigests(
                table, dsId, availablePlain, insert, reloadConnection,
                reload.whereSql, reload.whereParams);
        if (digests.isEmpty()) {
            return false;
        }

        List<String> computedTargets = new ArrayList<String>(targets.size());
        for (String target : targets) {
            if (getIgnoreCase(digests, target) != null) {
                computedTargets.add(target);
            }
        }
        DigestRewriteResult rewrite = DigestSqlRewriter.tryAppendTargets(
                boundSql.getSql(), computedTargets);
        if (rewrite.getWarnMessage() != null) {
            log.warn("【securt-kit】MyBatis digest SQL was not rewritten: {}", rewrite.getWarnMessage());
        }

        List<ParameterMapping> mappings = originalMappings == null
                ? new ArrayList<ParameterMapping>()
                : new ArrayList<ParameterMapping>(originalMappings);
        Configuration mappingConfiguration = new Configuration();
        Set<String> appendedTargets = new HashSet<String>();
        boolean mappingChanged = false;
        List<String> rewriteTargets = rewrite.getAppendedTargetFields();
        List<Integer> rewriteIndexes = rewrite.getAppendedParameterIndexes();
        for (int i = 0; i < rewriteTargets.size() && i < rewriteIndexes.size(); i++) {
            String target = rewriteTargets.get(i);
            String digest = getIgnoreCase(digests, target);
            if (digest == null) {
                continue;
            }
            String property = digestProperty(target);
            int mappingIndex = Math.min(Math.max(rewriteIndexes.get(i) - 1, 0), mappings.size());
            mappings.add(mappingIndex,
                    new ParameterMapping.Builder(mappingConfiguration, property, String.class).build());
            boundSql.setAdditionalParameter(property, digest);
            appendedTargets.add(target.toLowerCase(Locale.ROOT));
            mappingChanged = true;
        }

        for (Map.Entry<Integer, ColumnTableDto> entry : columnsByIndex.entrySet()) {
            String target = entry.getValue().getSourceColumn();
            String digest = getIgnoreCase(digests, target);
            int mappingIndex = entry.getKey() - 1;
            if (digest == null || mappingIndex < 0 || mappingIndex >= mappings.size()
                    || appendedTargets.contains(target.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String property = digestProperty(target);
            mappings.set(mappingIndex,
                    new ParameterMapping.Builder(mappingConfiguration, property, String.class).build());
            boundSql.setAdditionalParameter(property, digest);
            mappingChanged = true;
        }

        MetaObject boundSqlMetaObject = SystemMetaObject.forObject(boundSql);
        if (rewrite.isRewritten()) {
            boundSqlMetaObject.setValue("sql", rewrite.getSql());
        }
        if (mappingChanged) {
            boundSqlMetaObject.setValue("parameterMappings", mappings);
        }
        return rewrite.isRewritten() || mappingChanged;
    }

    private static String singleDigestTable(Set<String> tables, String datasourceId) {
        String matched = null;
        if (tables == null) {
            return null;
        }
        for (String table : tables) {
            if (!DigestConfigRegistry.hasDigest(table, datasourceId)) {
                continue;
            }
            if (matched != null && !equalsIgnoreCase(matched, table)) {
                throw new SecurtKitException("Digest write supports a single configured table only");
            }
            matched = table;
        }
        return matched;
    }

    private static boolean isInsert(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql) instanceof Insert;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSupportedWrite(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            return statement instanceof Insert || statement instanceof Update;
        } catch (Exception e) {
            return false;
        }
    }

    private static ReloadContext buildReloadContext(
            String sql, Map<Integer, Object> valuesByIndex,
            Map<Integer, ColumnTableDto> columnsByIndex, String datasourceId) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Update) || ((Update) statement).getWhere() == null) {
                return ReloadContext.EMPTY;
            }
            String whereSql = ((Update) statement).getWhere().toString();
            int totalCount = countPlaceholders(sql);
            int whereCount = countPlaceholders(whereSql);
            List<Object> whereParams = new ArrayList<Object>(whereCount);
            FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
            for (int index = totalCount - whereCount + 1; index <= totalCount; index++) {
                Object value = valuesByIndex.get(index);
                ColumnTableDto column = columnsByIndex.get(index);
                if (value instanceof String && needEncrypt(cryptoService, column, datasourceId)) {
                    String encrypted = encryptValue(
                            cryptoService, column, (String) value, datasourceId, index);
                    whereParams.add(encrypted == null ? value : encrypted);
                } else {
                    whereParams.add(value);
                }
            }
            return new ReloadContext(whereSql, whereParams);
        } catch (Exception e) {
            throw new SecurtKitException("Cannot resolve UPDATE WHERE parameters for digest RELOAD", e);
        }
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; sql != null && i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    private static boolean isMultiRowInsert(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Insert)
                    || !(((Insert) statement).getSelect() instanceof Values)) {
                return false;
            }
            Values values = (Values) ((Insert) statement).getSelect();
            return values.getExpressions() != null
                    && values.getExpressions().size() > 1
                    && values.getExpressions().get(0)
                    instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList;
        } catch (Exception e) {
            return false;
        }
    }

    private static String digestProperty(String target) {
        return "__securtkit_digest_" + target.toLowerCase(Locale.ROOT);
    }

    private static String getIgnoreCase(Map<String, String> values, String key) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (equalsIgnoreCase(entry.getKey(), key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static final class ReloadContext {
        private static final ReloadContext EMPTY =
                new ReloadContext(null, Collections.emptyList());

        private final String whereSql;
        private final List<Object> whereParams;

        private ReloadContext(String whereSql, List<Object> whereParams) {
            this.whereSql = whereSql;
            this.whereParams = whereParams;
        }
    }

    /**
     * 判断参数对象本身就是待绑定的标量值
     * <p>
     * Mapper 方法只有一个未加 {@code @Param} 的简单参数时，MyBatis 会把参数对象原样绑定到
     * 每个占位符（见 {@code DefaultParameterHandler} 对 TypeHandler 的判断），
     * 此时属性路径无效，只能整体替换参数对象。
     * </p>
     *
     * @param parameter MyBatis 参数对象，可为 null
     * @return true 表示需要走 {@link #encryptScalarParameter} 分支
     */
    public static boolean isScalarStringParameter(Object parameter) {
        return parameter instanceof String;
    }

    /**
     * 加密标量参数（参数对象本身即待绑定值）
     *
     * @param parameter    参数对象，必须是 String
     * @param boundSql     当前语句的 BoundSql
     * @param parseResult  core 的 SQL 解析结果
     * @param datasourceId 数据源标识
     * @return 密文；无需加密或加密未生效时返回 null
     */
    public static String encryptScalarParameter(Object parameter, BoundSql boundSql,
                                                Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
                                                String datasourceId) {
        if (!isScalarStringParameter(parameter)) {
            return null;
        }
        Map<Integer, ColumnTableDto> indexMap = buildParameterIndexMap(parseResult);
        if (indexMap.isEmpty()) {
            return null;
        }
        List<ParameterMapping> parameterMappings = boundSql == null ? null : boundSql.getParameterMappings();
        int mappingCount = parameterMappings == null ? 0 : parameterMappings.size();
        if (mappingCount == 0) {
            return null;
        }

        String dsId = normalizeDatasourceId(datasourceId);
        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
        String plain = (String) parameter;

        // 标量参数会被绑定到所有占位符，取第一个需要加密的字段即可
        for (int parameterIndex = 1; parameterIndex <= mappingCount; parameterIndex++) {
            ColumnTableDto dto = indexMap.get(parameterIndex);
            if (!needEncrypt(cryptoService, dto, dsId)) {
                continue;
            }
            String encrypted = encryptValue(cryptoService, dto, plain, dsId, parameterIndex);
            if (encrypted != null && !encrypted.equals(plain)) {
                if (log.isDebugEnabled()) {
                    log.debug("Encrypted scalar MyBatis parameter [index={}, table={}, column={}, datasource-id={}]",
                            parameterIndex, dto.getSourceTableName(), dto.getSourceColumn(), dsId);
                }
                return encrypted;
            }
        }
        return null;
    }

    /**
     * 加密参数对象上的字段
     * <p>
     * 属性值可能来自两处：参数对象本身（{@code #{phone}}、{@code #{user.phone}}）或 BoundSql
     * 附加参数（动态 SQL / {@code foreach} 产生的 {@code __frch_item_0}、{@code __frch_item_0.phone}）。
 * 两者都按 MyBatis 的 MetaObject 语义读写，因此 {@code foreach} 中的实体元素同样可被加密。
 * MyBatis-Plus Wrapper 条件值（{@code ew.paramNameValuePairs.MPGENVALn}）由
 * {@link MpWrapperParamSupport} 直接改写 Map，避免深层 MetaObject 路径漏加密。
 * </p>
     *
     * @param parameter    MyBatis 参数对象，可能是实体、Map 或 {@code ParamMap}，可为 null
     * @param boundSql     当前语句的 BoundSql，不能为 null
     * @param parseResult  core 的 SQL 解析结果（key 为占位符映射）
     * @param datasourceId 数据源标识，为空按默认数据源处理
     * @return 被改写的属性路径 → 原始明文，供 {@link #restoreParameters} 还原；无改写时返回空 Map
     */
    public static Map<String, Object> encryptParameters(Object parameter, BoundSql boundSql,
                                                       Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult,
                                                       String datasourceId) {
        if (boundSql == null) {
            return Collections.emptyMap();
        }
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, ColumnTableDto> indexMap = buildParameterIndexMap(parseResult);
        if (indexMap.isEmpty()) {
            return Collections.emptyMap();
        }

        String dsId = normalizeDatasourceId(datasourceId);
        FieldCryptoService cryptoService = FieldCryptoServiceHolder.get();
        MetaObject parameterMetaObject = parameter == null ? null : SystemMetaObject.forObject(parameter);
        Map<String, Object> originals = new LinkedHashMap<>();

        try {
            for (int i = 0; i < parameterMappings.size(); i++) {
                int parameterIndex = i + 1;
                ColumnTableDto dto = indexMap.get(parameterIndex);
                if (!needEncrypt(cryptoService, dto, dsId)) {
                    continue;
                }

                String property = parameterMappings.get(i).getProperty();
                if (StrUtil.isBlank(property)) {
                    continue;
                }
                if (originals.containsKey(property)) {
                    // 同一属性被多个占位符引用时只加密一次，避免二次加密
                    if (log.isDebugEnabled()) {
                        log.debug("Property already encrypted, skip [property={}, index={}]", property, parameterIndex);
                    }
                    continue;
                }

                Object raw = readValue(parameter, parameterMetaObject, boundSql, property);
                if (raw == null) {
                    continue;
                }
                if (!(raw instanceof String)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skip non-String parameter [property={}, index={}, type={}]",
                                property, parameterIndex, raw.getClass().getName());
                    }
                    continue;
                }

                String plain = (String) raw;
                String encrypted = encryptValue(cryptoService, dto, plain, dsId, parameterIndex);
                if (encrypted == null || encrypted.equals(plain)) {
                    continue;
                }
                if (writeValue(parameter, parameterMetaObject, boundSql, property, encrypted)) {
                    originals.put(property, plain);
                    if (log.isDebugEnabled()) {
                        log.debug("Encrypted MyBatis parameter [property={}, index={}, table={}, column={}, datasource-id={}]",
                                property, parameterIndex, dto.getSourceTableName(), dto.getSourceColumn(), dsId);
                    }
                }
            }
        } catch (RuntimeException e) {
            // 中途失败时先还原已改写的属性，避免业务对象残留密文
            restoreParameters(parameter, parameterMetaObject, boundSql, originals);
            throw e;
        }

        return originals;
    }

    /**
     * 还原被加密改写的参数
     *
     * @param parameter MyBatis 参数对象，可为 null
     * @param boundSql  当前语句的 BoundSql，可为 null
     * @param originals {@link #encryptParameters} 返回的属性路径 → 原始明文
     */
    public static void restoreParameters(Object parameter, BoundSql boundSql, Map<String, Object> originals) {
        if (originals == null || originals.isEmpty()) {
            return;
        }
        restoreParameters(parameter,
                parameter == null ? null : SystemMetaObject.forObject(parameter),
                boundSql,
                originals);
    }

    /**
     * 判断是否存在「仅写入 BoundSql 附加参数」的改写
     * <p>
     * {@code foreach} 中的不可变元素（如 {@code List<String>} 的 IN 条件）只能写进 BoundSql
     * 的附加参数，调用方必须保证下游执行使用的是同一个 BoundSql 实例，否则改写会丢失。
     * </p>
     * <p>
     * MP Wrapper 的 {@code ew.paramNameValuePairs.*} 写在 Wrapper 自身的 Map 上，不需要透传 BoundSql。
     * </p>
     *
     * @param boundSql  当前语句的 BoundSql
     * @param originals {@link #encryptParameters} 的返回值
     * @return true 表示需要把改写后的 BoundSql 透传给下游执行
     */
    public static boolean hasBoundSqlOnlyRewrite(BoundSql boundSql, Map<String, Object> originals) {
        if (boundSql == null || originals == null || originals.isEmpty()) {
            return false;
        }
        for (String property : originals.keySet()) {
            if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
                continue;
            }
            if (property.indexOf('.') < 0 && hasAdditionalParameter(boundSql, property)) {
                return true;
            }
        }
        return false;
    }

    private static void restoreParameters(Object parameter, MetaObject parameterMetaObject, BoundSql boundSql,
                                          Map<String, Object> originals) {
        if (originals == null || originals.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : originals.entrySet()) {
            if (!writeValue(parameter, parameterMetaObject, boundSql, entry.getKey(), entry.getValue())) {
                log.warn("【securt-kit】Failed to restore plain value for property: {}", entry.getKey());
            }
        }
    }

    private static boolean needEncrypt(FieldCryptoService cryptoService, ColumnTableDto dto, String datasourceId) {
        if (dto == null || StrUtil.isBlank(dto.getSourceTableName()) || StrUtil.isBlank(dto.getSourceColumn())) {
            return false;
        }
        return cryptoService.needEncrypt(dto.getSourceTableName(), dto.getSourceColumn(), datasourceId);
    }

    /**
     * 执行单个值的加密（含 LIKE 前置处理）
     *
     * @return 密文；LIKE 处理器要求跳过时返回 null
     */
    private static String encryptValue(FieldCryptoService cryptoService, ColumnTableDto dto,
                                       String plain, String datasourceId, int parameterIndex) {
        String table = dto.getSourceTableName();
        String column = dto.getSourceColumn();
        String plainToEncrypt = plain;

        if (dto.getMatchType() == ParameterMatchType.LIKE) {
            LikeHandleResult likeResult = LikePatternHandlerHolder.getHandler().handle(
                    plain, new LikeHandleContext(table, column, datasourceId, parameterIndex));
            if (likeResult == null || likeResult.getAction() == LikeHandleResult.Action.SKIP) {
                log.debug("LIKE handler skip encryption [table={}, column={}, index={}]: {}",
                        table, column, parameterIndex,
                        likeResult != null ? likeResult.getMessage() : "null result");
                return null;
            }
            if (likeResult.getAction() == LikeHandleResult.Action.REJECT) {
                throw new IllegalArgumentException(likeResult.getMessage() != null
                        ? likeResult.getMessage()
                        : "LIKE pattern rejected by LikePatternHandler");
            }
            plainToEncrypt = likeResult.getValueForEncrypt();
        }

        return cryptoService.encrypt(table, column, plainToEncrypt, datasourceId);
    }

    /**
     * 构建占位符序号到字段信息的映射，与 JDBC 通道保持一致（序号从 1 开始）
     */
    private static Map<Integer, ColumnTableDto> buildParameterIndexMap(
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult) {
        if (parseResult == null || parseResult.getKey() == null || parseResult.getKey().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, ColumnTableDto> indexMap = new HashMap<>();
        for (ColumnTableDto dto : parseResult.getKey().values()) {
            if (dto == null) {
                continue;
            }
            Integer index = dto.getInsertFieldIndex();
            if (index != null && index > 0) {
                indexMap.putIfAbsent(index, dto);
            }
        }
        return indexMap;
    }

    /**
     * 读取属性值
     * <p>
     * 优先走 MP Wrapper 的 {@code paramNameValuePairs} 直读；再尝试 BoundSql 附加参数与 MetaObject。
     * MetaObject 对 Map 根对象的复合路径可能抛异常，捕获后按「跳过」处理。
     * </p>
     */
    private static Object readValue(Object parameter, MetaObject parameterMetaObject,
                                    BoundSql boundSql, String property) {
        if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
            Object wrapperValue = MpWrapperParamSupport.read(parameter, property);
            if (wrapperValue != null) {
                return wrapperValue;
            }
        }
        if (hasAdditionalParameter(boundSql, property)) {
            try {
                return boundSql.getAdditionalParameter(property);
            } catch (Exception e) {
                log.debug("Failed to read additional parameter [property={}]: {}", property, e.getMessage());
            }
        }
        if (parameterMetaObject == null) {
            return null;
        }
        try {
            return parameterMetaObject.getValue(property);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Parameter property not readable, skip [property={}]: {}", property, e.getMessage());
            }
            return null;
        }
    }

    /**
     * 写入属性值
     * <p>
     * MP Wrapper 路径会同时尝试写入 {@code paramNameValuePairs} 与 BoundSql 附加参数，
     * 保证 ParameterHandler 无论从哪一侧取值都能拿到密文。
     * </p>
     */
    private static boolean writeValue(Object parameter, MetaObject parameterMetaObject, BoundSql boundSql,
                                      String property, Object value) {
        boolean written = false;

        if (MpWrapperParamSupport.isWrapperParamProperty(property)) {
            if (MpWrapperParamSupport.write(parameter, property, value)) {
                written = true;
            }
            // BoundSql 附加参数里若已有 ew 根对象，也同步一份（与 Wrapper Map 通常是同一引用）
            if (hasAdditionalParameter(boundSql, property)) {
                try {
                    boundSql.setAdditionalParameter(property, value);
                    written = true;
                } catch (Exception e) {
                    log.debug("Failed to write Wrapper value to additional parameter [property={}]: {}",
                            property, e.getMessage());
                }
            }
            if (written) {
                return true;
            }
        }

        if (hasAdditionalParameter(boundSql, property)) {
            try {
                boundSql.setAdditionalParameter(property, value);
                return true;
            } catch (Exception e) {
                log.debug("Failed to write additional parameter [property={}]: {}", property, e.getMessage());
            }
        }
        if (parameterMetaObject == null) {
            return false;
        }
        try {
            parameterMetaObject.setValue(property, value);
            return true;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Parameter property not writable, skip [property={}]: {}", property, e.getMessage());
            }
            return false;
        }
    }

    private static boolean hasAdditionalParameter(BoundSql boundSql, String property) {
        if (boundSql == null || StrUtil.isBlank(property)) {
            return false;
        }
        try {
            return boundSql.hasAdditionalParameter(property);
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeDatasourceId(String datasourceId) {
        return StrUtil.isBlank(datasourceId) ? DatasourceIdResolver.DEFAULT_DATASOURCE_ID : datasourceId;
    }
}
