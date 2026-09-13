package io.github.genkidoudou.monitor.dto;

import lombok.Data;

@Data
public class RowVerifyRequest {
    private String datasourceId;
    private String table;
    private Object id;
    private String idColumn;
}
