package io.github.hexlodev.ui.monitor.dto;

import io.github.hexlodev.ui.monitor.security.SafeInput;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
    @NotNull(message = "SQL 语句不能为空")
    @SafeInput(maxLength = 50 * 1024, message = "SQL 语句过长")
    private String sql;

    /**
     * 分页大小（默认 10）
     */
    @Min(value = 1, message = "分页大小必须大于 0")
    @Max(value = 100, message = "分页大小不能超过 100")
    private Integer pageSize = 10;

    /**
     * 页码（从 1 开始，默认 1）
     */
    @Min(value = 1, message = "页码必须大于 0")
    private Integer pageNum = 1;
}

