package io.github.genkidoudou.playground.dto;

import lombok.Data;

/**
 * 复杂查询固定 action 请求
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class ComplexRunRequest {
    private String datasourceId;
    /**
     * like-user | page-user | join-orders | batch-insert-user | multi-ds-compare
     */
    private String action;
    private String pattern;
    private String userName;
    private Integer pageSize;
    private Integer batchSize;
}
