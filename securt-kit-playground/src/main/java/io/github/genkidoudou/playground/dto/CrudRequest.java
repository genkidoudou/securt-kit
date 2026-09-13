package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRUD 请求体
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
public class CrudRequest {
    private String datasourceId;
    private String table;
    /** 主键 / 查询条件：id */
    private Long id;
    /** 写入字段（明文逻辑值） */
    private Map<String, Object> fields = new LinkedHashMap<>();
    /** query 时可选 LIMIT */
    private Integer limit;
}
