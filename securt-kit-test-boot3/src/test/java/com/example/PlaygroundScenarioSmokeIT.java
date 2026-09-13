package com.example;

import com.example.playground.PlaygroundWebConfig;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.ScenarioRunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {TestApplication3.class, PlaygroundWebConfig.class},
        properties = {
                "mybatis-plus.type-aliases-package=com.example.entity",
                "mybatis.type-aliases-package=com.example.entity"
        })
class PlaygroundScenarioSmokeIT {

    @Autowired
    private PlaygroundEngine engine;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void prepareSingleEqSeed() throws Exception {
        String cipherPhone = FieldCryptoServiceHolder.get().encrypt("user", "phone", "13800138000", null);
        String cipherName = FieldCryptoServiceHolder.get().encrypt("user", "name", "张三", null);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS \"user\" ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), phone VARCHAR(100), "
                    + "age INT, email VARCHAR(100))");
            statement.execute("CREATE TABLE IF NOT EXISTS \"orders\" ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, order_no VARCHAR(50), "
                    + "customer_name VARCHAR(100), customer_phone VARCHAR(100), amount DECIMAL(10,2), "
                    + "status VARCHAR(20))");
            statement.execute("DELETE FROM \"orders\"");
            statement.execute("DELETE FROM \"user\"");
            statement.execute("INSERT INTO \"user\" (id, name, phone, age, email) VALUES (1, '"
                    + cipherName.replace("'", "''") + "', '"
                    + cipherPhone.replace("'", "''") + "', 25, 'zhangsan@example.com')");
            statement.execute("INSERT INTO \"orders\" (id, user_id, order_no, customer_name, customer_phone, amount, status) "
                    + "VALUES (1, 1, 'ORD-1', '" + cipherName.replace("'", "''") + "', '"
                    + cipherPhone.replace("'", "''") + "', 100.00, 'PAID')");
        }
    }

    @Test
    void listAndRunSingleEqSmoke() {
        ApiResponse<List<Map<String, Object>>> list = engine.listScenarios();
        assertTrue(list.isSuccess(), list.getMessage());
        assertTrue(list.getData().stream().anyMatch(m -> "single-eq".equals(m.get("id"))));
        assertTrue(list.getData().stream().anyMatch(m -> "join-user-orders".equals(m.get("id"))));
        assertTrue(list.getData().stream().anyMatch(m -> "func-on-cipher".equals(m.get("id"))));

        Map<String, Object> singleEq = list.getData().stream()
                .filter(m -> "single-eq".equals(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("single-eq missing"));
        String expectedCipher = FieldCryptoServiceHolder.get().encrypt("user", "phone", "13800138000", null);
        assertTrue(String.valueOf(singleEq.get("exampleSqlCipher")).contains(expectedCipher));

        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setScenarioId("single-eq");
        request.setParams(Collections.<String, Object>singletonMap("phone", "13800138000"));
        ApiResponse<Map<String, Object>> run = engine.runScenario(request);
        assertTrue(run.isSuccess(), run.getMessage());
        assertNotNull(run.getData().get("plainRows"));
        assertNotNull(run.getData().get("cipherRows"));
        assertNotNull(run.getData().get("sqlMeta"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plain = (List<Map<String, Object>>) run.getData().get("plainRows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cipher = (List<Map<String, Object>>) run.getData().get("cipherRows");
        assertFalse(plain.isEmpty(), "prepared seed should hit plaintext QueryWrapper path");
        assertFalse(cipher.isEmpty());
        Object plainPhone = valueIgnoreCase(plain.get(0), "phone");
        Object cipherPhone = valueIgnoreCase(cipher.get(0), "phone");
        assertNotEquals(String.valueOf(plainPhone), String.valueOf(cipherPhone));
        @SuppressWarnings("unchecked")
        Map<String, Object> sqlMeta = (Map<String, Object>) run.getData().get("sqlMeta");
        assertTrue(String.valueOf(sqlMeta.get("sql")).contains("QueryWrapper")
                || String.valueOf(sqlMeta.get("sql")).toLowerCase().contains("basemapper"));
    }

    private static Object valueIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
