package io.github.genkidoudou.monitor.dto;

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
     * 查询结果数据（解密视图，兼容旧字段）
     */
    private List<Map<String, Object>> data;

    /**
     * 明文/解密行（与 data 相同，便于双视图）
     */
    private List<Map<String, Object>> plainRows;

    /**
     * 密文行（skip-comment 读取）
     */
    private List<Map<String, Object>> cipherRows;

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

