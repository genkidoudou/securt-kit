package io.github.test;

import com.example.playground.PlaygroundWebConfiguration;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.PersonPreflight;
import io.github.genkidoudou.playground.dto.PersonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {TestApplication.class, PlaygroundWebConfiguration.class})
@ActiveProfiles("jdbc")
class PlaygroundPersonJdbcIT {

    @Autowired
    private PlaygroundEngine engine;

    @BeforeEach
    void resetTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS playground_person");
            statement.execute("CREATE TABLE playground_person (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255), phone VARCHAR(255), id_card VARCHAR(255), age INT, "
                    + "row_digest VARCHAR(255))");
        }
    }

    @Test
    void createListUnderJdbcModeKeepsBusinessPlainAndRawCipher() {
        ApiResponse<PersonPreflight> preflight = engine.personPreflight(null);
        assertTrue(preflight.getData().isReady(), preflight.getMessage());
        assertTrue(preflight.getData().getChecks().stream()
                .anyMatch(c -> "encrypt-mode-info".equals(c.getId())
                        && String.valueOf(c.getMessage()).contains("JDBC")));

        PersonRequest create = new PersonRequest();
        create.setName("JDBC用户");
        create.setPhone("13800138888");
        create.setIdCard("110101199001011299");
        create.setAge(30);
        assertNotNull(engine.personCreate(create).getData().get("id"));

        PersonRequest business = new PersonRequest();
        business.setView("business");
        List<Map<String, Object>> businessRows = rows(engine.personList(business));
        assertEquals("13800138888", businessRows.get(0).get("phone"));

        PersonRequest raw = new PersonRequest();
        raw.setView("raw");
        assertNotEquals("13800138888", rows(engine.personList(raw)).get(0).get("phone"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(ApiResponse<Map<String, Object>> response) {
        assertTrue(response.isSuccess(), response.getMessage());
        return (List<Map<String, Object>>) response.getData().get("rows");
    }
}
