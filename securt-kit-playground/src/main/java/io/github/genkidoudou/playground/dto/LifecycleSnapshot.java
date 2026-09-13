package io.github.genkidoudou.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence returned by one lifecycle operation.
 */
@Data
public class LifecycleSnapshot {

    private String step;
    private String status;
    private Long recordId;
    private String message;
    private Map<String, Object> requestPlaintext = new LinkedHashMap<>();
    private Map<String, Object> rawDatabaseRow = new LinkedHashMap<>();
    private Map<String, Object> businessRow = new LinkedHashMap<>();
    private DigestVerification digestVerification;
    private List<Assertion> assertions = new ArrayList<>();
    private ErrorDetail error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assertion {
        private String id;
        private boolean passed;
        private String expected;
        private String actual;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DigestVerification {
        private String status;
        private List<String> sourceFields;
        private String digestField;
        private String detail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String category;
        private String detail;
    }
}
