package io.github.test;

import com.example.TestApplication2;
import com.example.playground.PlaygroundWebConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {TestApplication2.class, PlaygroundWebConfiguration.class},
        properties = {
                "mybatis-plus.type-aliases-package=com.example.entity",
                "mybatis.type-aliases-package=com.example.entity"
        })
class PlaygroundScenarioIT {

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
            statement.execute("DELETE FROM orders");
            statement.execute("DELETE FROM \"user\"");
            statement.execute("INSERT INTO \"user\" (id, name, phone, age, email) VALUES (1, '"
                    + cipherName.replace("'", "''") + "', '"
                    + cipherPhone.replace("'", "''") + "', 25, 'zhangsan@example.com')");
            statement.execute("INSERT INTO orders (id, user_id, order_no, customer_name, customer_phone, amount, status) "
                    + "VALUES (1, 1, 'ORD-1', '" + cipherName.replace("'", "''") + "', '"
                    + cipherPhone.replace("'", "''") + "', 100.00, 'PAID')");
        }
    }

    @Test
    void catalogContainsFixedScenarios() {
        ApiResponse<List<Map<String, Object>>> response = engine.listScenarios();
        assertTrue(response.isSuccess());
        List<Map<String, Object>> list = response.getData();
        assertEquals(6, list.size());
        assertTrue(list.stream().anyMatch(m -> "single-eq".equals(m.get("id"))));
        assertTrue(list.stream().anyMatch(m -> "join-user-orders".equals(m.get("id"))));
        assertTrue(list.stream().anyMatch(m -> "like-phone".equals(m.get("id"))));
        assertTrue(list.stream().anyMatch(m -> "column-alias".equals(m.get("id"))));
        assertTrue(list.stream().anyMatch(m -> "table-alias".equals(m.get("id"))));
        assertTrue(list.stream().anyMatch(m -> "func-on-cipher".equals(m.get("id"))));
        assertTrue(list.stream().allMatch(m -> Boolean.TRUE.equals(m.get("available"))),
                "runner should mark scenarios available when user.phone is encrypted");
        Map<String, Object> singleEq = list.stream()
                .filter(m -> "single-eq".equals(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("single-eq missing"));
        String cipherSql = String.valueOf(singleEq.get("exampleSqlCipher"));
        assertTrue(cipherSql.contains("SECURT_SKIP"));
        String expectedCipher = io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder.get()
                .encrypt("user", "phone", "13800138000", null);
        assertTrue(cipherSql.contains(expectedCipher),
                "cipher SQL must embed FieldCryptoService.encrypt result; sql=" + cipherSql
                        + " expected=" + expectedCipher);
    }

    @Test
    void singleEqPlaintextHitsCipherSeedAndCipherDiffers() {
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setScenarioId("single-eq");
        request.setParams(Collections.<String, Object>singletonMap("phone", "13800138000"));
        ApiResponse<Map<String, Object>> response = engine.runScenario(request);
        assertTrue(response.isSuccess(), response.getMessage());
        Map<String, Object> data = response.getData();
        assertNotNull(data.get("plainRows"));
        assertNotNull(data.get("cipherRows"));
        assertNotNull(data.get("sqlMeta"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plain = (List<Map<String, Object>>) data.get("plainRows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cipher = (List<Map<String, Object>>) data.get("cipherRows");
        assertFalse(plain.isEmpty(), "seed user.phone=13800138000(加密) should hit via QueryWrapper plaintext");
        assertFalse(cipher.isEmpty(), "cipher skip path should also hit encrypted phone");
        Object plainPhone = valueIgnoreCase(plain.get(0), "phone");
        Object cipherPhone = valueIgnoreCase(cipher.get(0), "phone");
        assertNotNull(plainPhone);
        assertNotNull(cipherPhone);
        assertNotEquals(String.valueOf(plainPhone), String.valueOf(cipherPhone));
        @SuppressWarnings("unchecked")
        Map<String, Object> sqlMeta = (Map<String, Object>) data.get("sqlMeta");
        assertTrue(String.valueOf(sqlMeta.get("sql")).contains("QueryWrapper")
                        || String.valueOf(sqlMeta.get("sql")).toLowerCase().contains("basemapper"),
                "plain path must identify BaseMapper/Wrapper: " + sqlMeta.get("sql"));
        assertNotNull(sqlMeta.get("encryptMode"));
    }

    private static Object valueIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
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

    @Test
    void joinLikeAliasAndFuncScenariosEncryptAndDecrypt() {
        Map<String, Object> join = run("join-user-orders",
                Collections.<String, Object>singletonMap("userId", "1"));
        assertThreeParts(join);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> joinPlain = (List<Map<String, Object>>) join.get("plainRows");
        assertFalse(joinPlain.isEmpty(), "join should return rows for userId=1");
        Object userPhone = valueIgnoreCase(joinPlain.get(0), "userPhone");
        Object userName = valueIgnoreCase(joinPlain.get(0), "userName");
        assertEquals("13800138000", String.valueOf(userPhone), "join plain path must decrypt userPhone");
        assertEquals("张三", String.valueOf(userName), "join plain path must decrypt userName");

        Map<String, Object> like = run("like-phone",
                Collections.<String, Object>singletonMap("pattern", "13800138000"));
        assertThreeParts(like);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> likePlain = (List<Map<String, Object>>) like.get("plainRows");
        assertFalse(likePlain.isEmpty(), "LIKE without wildcards must encrypt pattern and hit");
        assertEquals("13800138000", String.valueOf(valueIgnoreCase(likePlain.get(0), "phone")));
        @SuppressWarnings("unchecked")
        Map<String, Object> likeMeta = (Map<String, Object>) like.get("sqlMeta");
        assertTrue(String.valueOf(likeMeta.get("sql")).contains("selectByPhoneLike")
                || String.valueOf(likeMeta.get("sql")).contains("LIKE"));

        Map<String, Object> column = run("column-alias",
                Collections.<String, Object>singletonMap("phone", "13800138000"));
        assertThreeParts(column);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columnPlain = (List<Map<String, Object>>) column.get("plainRows");
        assertFalse(columnPlain.isEmpty(), "column-alias WHERE phone must encrypt bind and hit");
        assertEquals("13800138000", String.valueOf(valueIgnoreCase(columnPlain.get(0), "mobile")));

        Map<String, Object> table = run("table-alias",
                Collections.<String, Object>singletonMap("phone", "13800138000"));
        assertThreeParts(table);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tablePlain = (List<Map<String, Object>>) table.get("plainRows");
        assertFalse(tablePlain.isEmpty());
        assertEquals("13800138000", String.valueOf(valueIgnoreCase(tablePlain.get(0), "phone")));

        Map<String, Object> func = run("func-on-cipher",
                Collections.<String, Object>singletonMap("phone", "13800138000"));
        assertThreeParts(func);
        @SuppressWarnings("unchecked")
        Map<String, Object> funcMeta = (Map<String, Object>) func.get("sqlMeta");
        assertNotNull(funcMeta.get("limitation"));
        assertTrue(String.valueOf(funcMeta.get("sql")).contains("PlaygroundScenarioMapper"));
        // After placeholder-index fix, demo strategy may hit UPPER(cipher)=UPPER(encrypt(plain));
        // still require encryptMode and successful response rather than forcing non-empty.
        assertNotNull(funcMeta.get("encryptMode"));
    }

    private Map<String, Object> run(String id, Map<String, Object> params) {
        ScenarioRunRequest request = new ScenarioRunRequest();
        request.setScenarioId(id);
        request.setParams(params);
        ApiResponse<Map<String, Object>> response = engine.runScenario(request);
        assertTrue(response.isSuccess(), id + ": " + response.getMessage());
        return response.getData();
    }

    private void assertThreeParts(Map<String, Object> data) {
        assertTrue(data.containsKey("plainRows"));
        assertTrue(data.containsKey("cipherRows"));
        assertTrue(data.containsKey("sqlMeta"));
    }
}
