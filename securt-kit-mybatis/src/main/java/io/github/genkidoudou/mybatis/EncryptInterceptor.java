package io.github.genkidoudou.mybatis;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.DigestConfigRegistry;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.exception.SqlParseException;
import io.github.genkidoudou.core.parser.SecurtkitUtils;
import io.github.genkidoudou.core.parser.SqlParseCache;
import io.github.genkidoudou.core.parser.dto.ColumnTableDto;
import io.github.genkidoudou.core.parser.dto.FieldEncryptorInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * MyBatis 字段加解密插件（mode=MYBATIS）
 * <p>
 * 挂载在 {@link Executor} 上，执行前加密参数、执行后解密结果，配置、SQL 解析与加解密逻辑
 * 全部复用 securt-kit-core，与 JDBC 通道共用同一份加密清单和失败策略。
 * </p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>非 {@code MYBATIS} 模式直接透传，避免与 JDBC 通道双重加密</li>
 *   <li>取 BoundSql，命中 skip-comment 时透传</li>
 *   <li>解析表名，无需加密的语句透传（快速路径）</li>
 *   <li>解析 SQL 得到「占位符 → 表.列」与「结果列 → 表.列」映射</li>
 *   <li>加密参数（就地改写），执行 SQL，解密结果，最后还原参数明文</li>
 * </ol>
 *
 * <p>与其它插件的顺序：本插件读取 BoundSql 但不改写 SQL 文本，建议注册在分页插件之后
 * （即先执行分页改写，再执行参数加密），避免分页插件生成的 count 语句绕过加密。</p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
@Slf4j
public class EncryptInterceptor implements Interceptor {

    /**
     * 6 参数 query 签名的参数个数（此时 BoundSql 由调用方传入，改写可直接生效）
     */
    private static final int QUERY_ARGS_WITH_BOUND_SQL = 6;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!EncryptModeHolder.isMybatis()) {
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();
        if (args == null || args.length < 2 || !(args[0] instanceof MappedStatement)) {
            return invocation.proceed();
        }

        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameter = args[1];
        boolean query = "query".equals(invocation.getMethod().getName());
        boolean callerBoundSql = args.length == QUERY_ARGS_WITH_BOUND_SQL && args[5] instanceof BoundSql;

        BoundSql boundSql = resolveBoundSql(mappedStatement, parameter, args, callerBoundSql);
        if (boundSql == null) {
            return invocation.proceed();
        }

        String sql = boundSql.getSql();
        if (StrUtil.isBlank(sql)) {
            return invocation.proceed();
        }
        if (ConfigInitializer.shouldSkipByComment(sql)) {
            log.debug("Skip encryption by comment token [statement={}]", mappedStatement.getId());
            return invocation.proceed();
        }

        String datasourceId = DatasourceIdResolver.resolve();

        // 快速路径：SQL 涉及的表都没有配置加密字段时直接透传
        Set<String> tables = parseTableNames(sql);
        if (!SecurtkitUtils.needEncrypt(tables, datasourceId)
                && !hasConfiguredDigest(tables, datasourceId)) {
            if (log.isDebugEnabled()) {
                log.debug("No encrypted table involved, passthrough [statement={}, tables={}, datasource-id={}]",
                        mappedStatement.getId(), tables, datasourceId);
            }
            return invocation.proceed();
        }

        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseResult = parseSql(sql, datasourceId, tables);
        if (parseResult == null) {
            return invocation.proceed();
        }

        // 摘要必须基于业务明文计算；BoundSql 改写完成后重新解析索引，再执行字段加密。
        boolean digestRewritten = ParameterEncryptHelper.applyDigests(
                parameter, boundSql, parseResult, tables, datasourceId,
                resolveConnection(invocation));
        if (digestRewritten) {
            parseResult = parseSql(boundSql.getSql(), datasourceId, tables);
            if (!callerBoundSql) {
                MappedStatement wrapped = wrapBoundSql(mappedStatement, boundSql);
                if (wrapped != null) {
                    args[0] = wrapped;
                }
            }
        }

        Map<String, Object> originals = Collections.emptyMap();
        if (ParameterEncryptHelper.isScalarStringParameter(parameter)) {
            // 参数对象本身就是待绑定值（单个未命名简单参数），只能整体替换
            String encrypted = ParameterEncryptHelper.encryptScalarParameter(parameter, boundSql, parseResult, datasourceId);
            if (encrypted != null) {
                args[1] = encrypted;
            }
        } else {
            originals = ParameterEncryptHelper.encryptParameters(parameter, boundSql, parseResult, datasourceId);
            if (!callerBoundSql && ParameterEncryptHelper.hasBoundSqlOnlyRewrite(boundSql, originals)) {
                // foreach 中的不可变元素只改写在 BoundSql 附加参数上，
                // 必须把当前 BoundSql 透传给下游，否则 Executor 会重新构建导致改写丢失
                MappedStatement wrapped = wrapBoundSql(mappedStatement, boundSql);
                if (wrapped != null) {
                    args[0] = wrapped;
                }
            }
        }

        // 一级缓存命中时返回的是上一次已解密的对象，必须跳过，避免重复解密
        boolean cached = query && isLocallyCached(invocation, mappedStatement, args, boundSql);

        try {
            Object result = invocation.proceed();
            if (query && result != null && !cached) {
                ResultDecryptHelper.decryptResult(result, parseResult, tables, datasourceId);
            }
            return result;
        } finally {
            ParameterEncryptHelper.restoreParameters(parameter, boundSql, originals);
        }
    }

    /**
     * 判断本次查询是否会命中 MyBatis 一级缓存
     * <p>
     * 结果解密是「就地改写映射对象」，而一级缓存持有的正是同一批对象引用，
     * 因此缓存命中时结果已是明文，重复解密会破坏数据。
     * </p>
     *
     * @return true 表示命中一级缓存，应跳过解密
     */
    private boolean isLocallyCached(Invocation invocation, MappedStatement mappedStatement,
                                    Object[] args, BoundSql boundSql) {
        if (!(invocation.getTarget() instanceof Executor) || args.length < 4) {
            return false;
        }
        if (args[3] != null) {
            // 传入 ResultHandler 时 MyBatis 不读取一级缓存，一定会重新映射
            return false;
        }
        try {
            Executor executor = (Executor) invocation.getTarget();
            CacheKey cacheKey;
            if (args.length == QUERY_ARGS_WITH_BOUND_SQL && args[4] instanceof CacheKey) {
                cacheKey = (CacheKey) args[4];
            } else {
                RowBounds rowBounds = args[2] instanceof RowBounds ? (RowBounds) args[2] : RowBounds.DEFAULT;
                cacheKey = executor.createCacheKey(mappedStatement, args[1], rowBounds, boundSql);
            }
            return executor.isCached(mappedStatement, cacheKey);
        } catch (Exception e) {
            log.debug("Failed to check local cache [statement={}]: {}", mappedStatement.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 本插件的行为完全由 securtkit.encryptor 配置驱动，无需插件级属性
    }

    /**
     * 获取当前语句的 BoundSql
     * <p>
     * 6 参数 query 使用调用方传入的实例（改写直接生效）；其余签名只能自行构建，
     * 此时对参数对象图的改写依然有效（下游按属性路径读取同一批对象）。
     * </p>
     */
    private BoundSql resolveBoundSql(MappedStatement mappedStatement, Object parameter,
                                     Object[] args, boolean callerBoundSql) {
        if (callerBoundSql) {
            return (BoundSql) args[5];
        }
        try {
            return mappedStatement.getBoundSql(parameter);
        } catch (Exception e) {
            log.debug("Failed to build BoundSql, passthrough [statement={}]: {}",
                    mappedStatement.getId(), e.getMessage());
            return null;
        }
    }

    private Set<String> parseTableNames(String sql) {
        try {
            Set<String> tables = SqlParseCache.parseTableNames(sql);
            return tables != null ? tables : Collections.<String>emptySet();
        } catch (Exception e) {
            log.warn("Failed to parse table names from SQL [sqlLength={}]: {}", sql.length(), e.getMessage());
            return Collections.emptySet();
        }
    }

    private boolean hasConfiguredDigest(Set<String> tables, String datasourceId) {
        for (String table : tables) {
            if (DigestConfigRegistry.hasDigest(table, datasourceId)) {
                return true;
            }
        }
        return false;
    }

    private Connection resolveConnection(Invocation invocation) {
        if (!(invocation.getTarget() instanceof Executor)) {
            return null;
        }
        try {
            return ((Executor) invocation.getTarget()).getTransaction().getConnection();
        } catch (Exception e) {
            log.debug("Failed to resolve connection for digest RELOAD: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 SQL 获取字段映射
     * <p>
     * 与 JDBC 通道一致：涉及加密表却解析失败时快速失败，避免明文落库。
     * </p>
     */
    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql, String datasourceId,
                                                                                    Set<String> tables) {
        try {
            return SecurtkitUtils.parseSql(sql, datasourceId);
        } catch (Exception e) {
            log.error("Failed to parse SQL for encryption [sqlLength={}, tables={}, datasource-id={}]: {}",
                    sql.length(), tables, datasourceId, e.getMessage(), e);
            throw new SqlParseException("SQL parsing failed for encryption", e, sql);
        }
    }

    /**
     * 用当前（已改写附加参数的）BoundSql 包装 MappedStatement，保证下游执行使用同一实例
     *
     * @return 包装后的 MappedStatement，复制失败时返回 null（调用方保持原状）
     */
    private MappedStatement wrapBoundSql(MappedStatement ms, BoundSql boundSql) {
        try {
            MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(),
                    parameterObject -> boundSql, ms.getSqlCommandType());
            builder.resource(ms.getResource());
            builder.parameterMap(ms.getParameterMap());
            builder.resultMaps(ms.getResultMaps());
            builder.resultSetType(ms.getResultSetType());
            builder.fetchSize(ms.getFetchSize());
            builder.timeout(ms.getTimeout());
            builder.statementType(ms.getStatementType());
            builder.keyGenerator(ms.getKeyGenerator());
            builder.keyProperty(join(ms.getKeyProperties()));
            builder.keyColumn(join(ms.getKeyColumns()));
            builder.resultSets(join(ms.getResultSets()));
            builder.databaseId(ms.getDatabaseId());
            builder.lang(ms.getLang());
            builder.resultOrdered(ms.isResultOrdered());
            builder.flushCacheRequired(ms.isFlushCacheRequired());
            builder.useCache(ms.isUseCache());
            builder.cache(ms.getCache());
            return builder.build();
        } catch (Exception e) {
            log.warn("【securt-kit】Failed to wrap MappedStatement [statement={}], IN parameters built by foreach "
                    + "may stay unencrypted: {}", ms.getId(), e.getMessage());
            return null;
        }
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return String.join(",", values);
    }
}
