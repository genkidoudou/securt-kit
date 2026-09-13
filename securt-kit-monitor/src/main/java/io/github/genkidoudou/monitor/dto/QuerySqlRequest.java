package io.github.genkidoudou.monitor.dto;

import lombok.Data;

/**
 * SQL 查询请求
 *
 * <p>纯 POJO，校验由 {@link io.github.genkidoudou.monitor.security.InputGuards} 在引擎中显式执行。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class QuerySqlRequest {
    /**
     * 要执行的 SQL 查询语句（只支持 SELECT）
     */
    private String sql;

    /**
     * 分页大小（默认 10，最大 100）
     */
    private Integer pageSize = 10;

    /**
     * 页码（从 1 开始，默认 1）
     */
    private Integer pageNum = 1;

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
