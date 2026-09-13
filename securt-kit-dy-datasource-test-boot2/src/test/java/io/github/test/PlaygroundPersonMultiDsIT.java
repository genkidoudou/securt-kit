package io.github.test;

import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.dto.PersonRequest;
import io.github.test.playground.PlaygroundWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {MultiDataSourceApplication2.class, PlaygroundWebConfig.class})
class PlaygroundPersonMultiDsIT {

    @Autowired
    private PlaygroundEngine engine;

    @Test
    void personCrudUsesEachSelectedDatasourceAndRejectsUnknownId() {
        for (String id : new String[]{"primary", "secondary", "third"}) {
            assertTrue(engine.personPreflight(id).getData().isReady(),
                    "preflight should be ready for " + id);

            PersonRequest create = new PersonRequest();
            create.setDatasourceId(id);
            create.setName("多源用户-" + id);
            create.setPhone("13800138000");
            create.setIdCard("110101199001011234");
            create.setAge(28);
            Long recordId = ((Number) engine.personCreate(create).getData().get("id")).longValue();

            PersonRequest business = new PersonRequest();
            business.setDatasourceId(id);
            business.setView("business");
            business.setPhone("13800138000");
            List<Map<String, Object>> businessRows = rows(engine.personList(business));
            assertEquals(1, businessRows.size(), id);
            assertEquals("13800138000", businessRows.get(0).get("phone"));

            PersonRequest raw = new PersonRequest();
            raw.setDatasourceId(id);
            raw.setView("raw");
            assertNotEquals("13800138000", rows(engine.personList(raw)).get(0).get("phone"), id);

            PersonRequest delete = new PersonRequest();
            delete.setDatasourceId(id);
            delete.setId(recordId);
            assertTrue(engine.personDelete(delete).isSuccess(), id);
        }

        PersonRequest missing = new PersonRequest();
        missing.setDatasourceId("missing");
        missing.setName("x");
        missing.setPhone("13800138000");
        missing.setIdCard("110101199001011234");
        assertEquals(400, engine.personCreate(missing).getCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(
            io.github.genkidoudou.playground.dto.ApiResponse<Map<String, Object>> response) {
        assertTrue(response.isSuccess(), response.getMessage());
        return (List<Map<String, Object>>) response.getData().get("rows");
    }
}
