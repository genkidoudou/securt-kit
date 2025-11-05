package io.github.hexlodev.ui.monitor.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 查询响应
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class QuerySqlResponse {
    /**
     * 执行的 SQL（包含分页）
     */
    private String executedSql;

    /**
     * 查询结果列名列表
     */
    private List<String> columns;

    /**
     * 查询结果数据（每行是一个 Map，key 为列名，value 为值）
     */
    private List<Map<String, Object>> data;

    /**
     * 总记录数（如果能够获取）
     */
    private Long totalCount;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;
}

