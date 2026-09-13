package io.github.genkidoudou.playground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * playground_person 就绪检查结果。
 */
@Data
public class PersonPreflight {
    private String datasourceId;
    private boolean ready;
    private List<Check> checks = new ArrayList<>();

    @Data
    public static class Check {
        private String id;
        private boolean passed;
        private String message;

        public Check() {
        }

        public Check(String id, boolean passed, String message) {
            this.id = id;
            this.passed = passed;
            this.message = message;
        }
    }
}
