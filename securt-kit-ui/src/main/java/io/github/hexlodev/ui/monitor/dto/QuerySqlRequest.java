package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

/**
 * SQL 查询请求
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
     * 分页大小（默认 10）
     */
    private Integer pageSize = 10;

    /**
     * 页码（从 1 开始，默认 1）
     */
    private Integer pageNum = 1;
}

