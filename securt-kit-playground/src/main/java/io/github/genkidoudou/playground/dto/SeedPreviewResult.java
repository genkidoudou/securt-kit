package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dual-table seed preview for the complex-query workbench.
 */
@Data
public class SeedPreviewResult {
    private List<Map<String, Object>> userRows = new ArrayList<>();
    private List<Map<String, Object>> ordersRows = new ArrayList<>();
    private String encryptNote;
    private Map<String, Object> limits = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userRows", userRows);
        m.put("ordersRows", ordersRows);
        m.put("encryptNote", encryptNote);
        m.put("limits", limits);
        return m;
    }
}
