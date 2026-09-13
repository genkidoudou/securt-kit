package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * SQL 加密请求
 *
 * <p>纯 POJO，校验由 {@link io.github.genkidoudou.monitor.security.InputGuards} 在引擎中显式执行。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class EncryptSqlRequest {
    /**
     * 要加密的 SQL 语句（包含实际值）
     */
    private String sql;

    /**
     * 数据源标识（可选，多数据源场景使用）
     *
     * <p>如果不指定，则使用默认数据源（"default"）</p>
     * <p>多数据源场景下，需要指定数据源标识以获取正确的加密配置</p>
     *
     * @since 1.1.0
     */
    private String datasourceId;
}
