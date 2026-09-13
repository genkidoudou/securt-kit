package io.github.genkidoudou.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-mutating readiness report for the fixed lifecycle demo.
 */
@Data
public class LifecyclePreflight {

    private boolean ready;
    private String datasourceId;
    private List<Check> checks = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Check {
        private String id;
        private boolean passed;
        private String message;
    }
}
