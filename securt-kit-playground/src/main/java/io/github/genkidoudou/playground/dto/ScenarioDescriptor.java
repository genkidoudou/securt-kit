package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog entry for a fixed complex-query scenario.
 */
@Data
public class ScenarioDescriptor {
    private String id;
    private String title;
    private String description;
    private boolean available = true;
    private String unavailableReason;
    private List<ParamSchema> params = new ArrayList<>();
    /** Recommended parameter values for the SQL workbench. */
    private Map<String, Object> sampleParams = new LinkedHashMap<>();
    /** Example SQL for the plaintext / encrypting path. */
    private String exampleSqlPlain;
    /** Example SQL for the ciphertext / SECURT_SKIP path. */
    private String exampleSqlCipher;
    /** One-line Chinese hint about related seed data. */
    private String sampleHint;

    @Data
    public static class ParamSchema {
        private String name;
        private String label;
        private String type = "string";
        private boolean required;
        private String placeholder;

        public ParamSchema() {
        }

        public ParamSchema(String name, String label, boolean required, String placeholder) {
            this.name = name;
            this.label = label;
            this.required = required;
            this.placeholder = placeholder;
        }
    }

    public static ScenarioDescriptor of(String id, String title, String description,
                                        List<ParamSchema> params) {
        ScenarioDescriptor d = new ScenarioDescriptor();
        d.setId(id);
        d.setTitle(title);
        d.setDescription(description);
        if (params != null) {
            d.setParams(params);
        }
        return d;
    }

    public ScenarioDescriptor unavailable(String reason) {
        this.available = false;
        this.unavailableReason = reason;
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("description", description);
        m.put("available", available);
        m.put("unavailableReason", unavailableReason);
        List<Map<String, Object>> paramMaps = new ArrayList<>();
        for (ParamSchema p : params) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.getName());
            pm.put("label", p.getLabel());
            pm.put("type", p.getType());
            pm.put("required", p.isRequired());
            pm.put("placeholder", p.getPlaceholder());
            paramMaps.add(pm);
        }
        m.put("params", paramMaps);
        m.put("sampleParams", sampleParams == null ? new LinkedHashMap<String, Object>() : sampleParams);
        m.put("exampleSqlPlain", exampleSqlPlain == null ? "" : exampleSqlPlain);
        m.put("exampleSqlCipher", exampleSqlCipher == null ? "" : exampleSqlCipher);
        m.put("sampleHint", sampleHint == null ? "" : sampleHint);
        return m;
    }
}
