package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-column evidence returned by scenario runs.
 */
@Data
public class ScenarioRunResult {
    private String scenarioId;
    private List<Map<String, Object>> plainRows = new ArrayList<>();
    private List<Map<String, Object>> cipherRows = new ArrayList<>();
    private Map<String, Object> sqlMeta = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scenarioId", scenarioId);
        m.put("plainRows", plainRows);
        m.put("cipherRows", cipherRows);
        m.put("sqlMeta", sqlMeta);
        return m;
    }
}
