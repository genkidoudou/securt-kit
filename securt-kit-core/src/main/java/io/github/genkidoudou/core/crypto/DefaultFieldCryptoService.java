package io.github.genkidoudou.core.crypto;

import cn.hutool.core.util.StrUtil;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.exception.EncryptionHandler;
import io.github.genkidoudou.core.logging.SqlLogger;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认字段加解密门面实现
 * <p>
 * 基于 {@link TableCache}（字段清单）、{@link StrategyCache}（策略实例）与
 * {@link EncryptionHandler}（失败策略）实现，行为与原 JDBC 通道内联逻辑保持一致。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
public class DefaultFieldCryptoService implements FieldCryptoService {

    /**
     * 默认数据源标识
     */
    private static final String DEFAULT_DATASOURCE_ID = "default";

    /**
     * 门面内日志使用的占位参数索引（非 JDBC 参数位）
     */
    private static final int NO_PARAMETER_INDEX = -1;

    @Override
    public boolean needEncrypt(String table, String column, String datasourceId) {
        return resolveStrategyClass(table, column, datasourceId) != null;
    }

    @Override
    public String encrypt(String table, String column, String plain, String datasourceId) {
        if (plain == null || StrUtil.isBlank(table) || StrUtil.isBlank(column)) {
            return plain;
        }

        final String ds = normalizeDatasourceId(datasourceId);
        Class<? extends FieldEncryptorStrategy> strategyClass = resolveStrategyClass(table, column, ds);
        if (strategyClass == null) {
            return plain;
        }

        try {
            FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
            String encrypted = EncryptionHandler.handleEncryption(
                    plain,
                    table,
                    column,
                    () -> {
                        String enc = strategy.encryption(plain);
                        SqlLogger.logEncryption(table, column, NO_PARAMETER_INDEX, ds, plain, enc);
                        return enc;
                    },
                    null
            );
            return encrypted != null ? encrypted : plain;
        } catch (Exception e) {
            log.warn("[ENCRYPTION ERROR] Failed to encrypt field [table={}, column={}, datasource-id={}]",
                    table, column, ds, e);
            return plain;
        }
    }

    @Override
    public String decrypt(String table, String column, String cipher, String datasourceId) {
        if (cipher == null || StrUtil.isBlank(table) || StrUtil.isBlank(column)) {
            return cipher;
        }

        final String ds = normalizeDatasourceId(datasourceId);
        Class<? extends FieldEncryptorStrategy> strategyClass = resolveStrategyClass(table, column, ds);
        if (strategyClass == null) {
            return cipher;
        }

        try {
            FieldEncryptorStrategy strategy = StrategyCache.getStrategy(strategyClass);
            String decrypted = EncryptionHandler.handleDecryption(
                    cipher,
                    table,
                    column,
                    () -> {
                        String dec = strategy.decryption(cipher);
                        SqlLogger.logDecryption(table, column, column, ds, cipher, dec);
                        return dec;
                    },
                    null
            );
            // 解密失败时降级策略返回原值，迁移期可兼容库中残留明文
            return decrypted != null ? decrypted : cipher;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to decrypt field [table={}, column={}, datasource-id={}], using original value. Error: {}",
                        table, column, ds, e.getMessage(), e);
            }
            return cipher;
        }
    }

    /**
     * 查询字段对应的加密策略类
     *
     * @param table        表名
     * @param column       列名
     * @param datasourceId 数据源标识
     * @return 策略类，未配置加密时返回 null
     */
    private Class<? extends FieldEncryptorStrategy> resolveStrategyClass(String table, String column, String datasourceId) {
        if (StrUtil.isBlank(table) || StrUtil.isBlank(column)) {
            return null;
        }
        try {
            return TableCache.getTableFieldEncryptStrategy(table, column, normalizeDatasourceId(datasourceId));
        } catch (Exception e) {
            log.debug("Failed to resolve encrypt strategy [table={}, column={}]: {}", table, column, e.getMessage());
            return null;
        }
    }

    private static String normalizeDatasourceId(String datasourceId) {
        return StrUtil.isBlank(datasourceId) ? DEFAULT_DATASOURCE_ID : datasourceId;
    }
}
