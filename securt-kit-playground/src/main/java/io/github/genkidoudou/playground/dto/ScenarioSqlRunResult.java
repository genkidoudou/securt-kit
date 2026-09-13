package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a playground sql-run call.
 */
@Data
public class ScenarioSqlRunResult {
    private List<Map<String, Object>> rows = new ArrayList<>();
    private Map<String, Object> sqlMeta = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", rows);
        m.put("sqlMeta", sqlMeta);
        return m;
    }
}
