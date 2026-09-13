package io.github.genkidoudou.playground.dto;

import lombok.Data;

/**
 * Request body for read-only SELECT execution on the complex-query workbench.
 */
@Data
public class ScenarioSqlRunRequest {
    private String sql;
    private boolean useSkip;
    private String datasourceId;
}
