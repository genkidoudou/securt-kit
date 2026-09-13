package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request body for POST /api/scenarios/run.json
 */
@Data
public class ScenarioRunRequest {
    private String scenarioId;
    private String datasourceId;
    private Map<String, Object> params = new LinkedHashMap<>();
}
