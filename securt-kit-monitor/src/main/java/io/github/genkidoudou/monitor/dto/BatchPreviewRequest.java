package io.github.genkidoudou.monitor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchPreviewRequest {
    private String datasourceId;
    private String table;
    private String where;
    /** encrypt | decrypt | sign */
    private String op;
    private String idColumn;
    private List<String> fields = new ArrayList<>();
}
