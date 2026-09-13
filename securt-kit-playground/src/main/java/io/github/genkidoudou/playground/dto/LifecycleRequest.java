package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateless lifecycle operation request.
 */
@Data
public class LifecycleRequest {

    private String datasourceId;
    private Long recordId;
    private String name;
    private String phone;
    private Integer age;
    private String email;
    private String tamperTarget;
    private String table;
    private Map<String, Object> fields = new LinkedHashMap<>();
}
