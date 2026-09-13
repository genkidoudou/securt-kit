package com.example;

import com.example.playground.PlaygroundWebConfig;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.PersonPreflight;
import io.github.genkidoudou.playground.dto.PersonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PlaygroundPersonIT.PersonTestApplication.class)
class PlaygroundPersonIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
    })
    @Import({PlaygroundWebConfig.class, MyFieldEncryptorStrategy.class})
    static class PersonTestApplication {
    }

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
    void createListRawUpdateFilterAndDelete() {
        ApiResponse<PersonPreflight> preflight = engine.personPreflight(null);
        assertTrue(preflight.getData().isReady(), preflight.getMessage());

        PersonRequest create = new PersonRequest();
        create.setName("演示用户");
        create.setPhone("13800138000");
        create.setIdCard("110101199001011234");
        create.setAge(28);
        Map<String, Object> created = engine.personCreate(create).getData();
        Long id = ((Number) created.get("id")).longValue();
        assertNotNull(id);

        PersonRequest business = new PersonRequest();
        business.setView("business");
        List<Map<String, Object>> businessRows = rows(engine.personList(business));
        assertEquals("13800138000", businessRows.get(0).get("phone"));

        PersonRequest raw = new PersonRequest();
        raw.setView("raw");
        assertNotEquals("13800138000", rows(engine.personList(raw)).get(0).get("phone"));

        PersonRequest update = new PersonRequest();
        update.setId(id);
        update.setName("演示用户");
        update.setPhone("13900139000");
        update.setIdCard("110101199001011234");
        update.setAge(29);
        assertTrue(engine.personUpdate(update).isSuccess());

        PersonRequest byPhone = new PersonRequest();
        byPhone.setView("business");
        byPhone.setPhone("13900139000");
        assertEquals(1, rows(engine.personList(byPhone)).size());

        PersonRequest delete = new PersonRequest();
        delete.setId(id);
        assertTrue(engine.personDelete(delete).isSuccess());
        assertEquals(0, rows(engine.personList(business)).size());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(ApiResponse<Map<String, Object>> response) {
        assertTrue(response.isSuccess(), response.getMessage());
        return (List<Map<String, Object>>) response.getData().get("rows");
    }
}
