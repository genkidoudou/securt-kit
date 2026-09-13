package io.github.genkidoudou.playground;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.ScenarioRunResult;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundScenarioDispatcherTest {

    private PlaygroundProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PlaygroundProperties();
        properties.setAuthEnabled(false);
    }

    @Test
    void listWithoutRunnerMarksAllUnavailable() {
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(
                new PlaygroundEngine(properties, null, null, null), properties);
        PlaygroundExchange ex = exchange("GET", "/api/scenarios.json", null);
        dispatcher.dispatch(ex);
        assertEquals(200, ex.getStatusCode());
        ApiResponse<?> body = JSONUtil.toBean(ex.getBodyString(), ApiResponse.class);
        assertTrue(body.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.getData();
        assertEquals(6, list.size());
        assertTrue(list.stream().anyMatch(m -> "single-eq".equals(m.get("id"))));
        assertTrue(list.stream().allMatch(m -> Boolean.FALSE.equals(m.get("available"))));
        assertTrue(String.valueOf(list.get(0).get("unavailableReason")).contains("PlaygroundScenarioRunner"));
    }

    @Test
    void runWithoutRunnerReturnsServiceUnavailable() {
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(
                new PlaygroundEngine(properties, null, null, null), properties);
        PlaygroundExchange ex = exchange("POST", "/api/scenarios/run.json",
                "{\"scenarioId\":\"single-eq\",\"params\":{\"phone\":\"13800138000\"}}");
        dispatcher.dispatch(ex);
        ApiResponse<?> body = JSONUtil.toBean(ex.getBodyString(), ApiResponse.class);
        assertEquals(503, ex.getStatusCode());
        assertFalse(body.isSuccess());
        assertTrue(body.getMessage().contains("PlaygroundScenarioRunner"));
    }

    @Test
    void scenarioApisRequireAuthWhenEnabled() {
        properties.setAuthEnabled(true);
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(
                new PlaygroundEngine(properties, null, null, null), properties);
        PlaygroundExchange list = exchange("GET", "/api/scenarios.json", null);
        dispatcher.dispatch(list);
        assertEquals(401, list.getStatusCode());

        PlaygroundExchange run = exchange("POST", "/api/scenarios/run.json",
                "{\"scenarioId\":\"single-eq\"}");
        dispatcher.dispatch(run);
        assertEquals(401, run.getStatusCode());
    }

    @Test
    void runWithRunnerReturnsThreeParts() {
        PlaygroundScenarioRunner runner = new PlaygroundScenarioRunner() {
            @Override
            public java.util.List<io.github.genkidoudou.playground.dto.ScenarioDescriptor> list() {
                return PlaygroundScenarioCatalog.baseline();
            }

            @Override
            public ScenarioRunResult run(String scenarioId, Map<String, Object> params, String datasourceId) {
                ScenarioRunResult result = new ScenarioRunResult();
                result.setScenarioId(scenarioId);
                result.setPlainRows(Collections.singletonList(Collections.<String, Object>singletonMap("phone", "138")));
                result.setCipherRows(Collections.singletonList(Collections.<String, Object>singletonMap("phone", "ENC")));
                Map<String, Object> meta = new HashMap<>();
                meta.put("encryptMode", "JDBC");
                meta.put("sql", "SELECT * FROM \"user\"");
                result.setSqlMeta(meta);
                return result;
            }
        };
        PlaygroundDispatcher dispatcher = new PlaygroundDispatcher(
                new PlaygroundEngine(properties, null, null, runner), properties);
        PlaygroundExchange ex = exchange("POST", "/api/scenarios/run.json",
                "{\"scenarioId\":\"single-eq\",\"params\":{\"phone\":\"138\"}}");
        dispatcher.dispatch(ex);
        assertEquals(200, ex.getStatusCode());
        ApiResponse<?> body = JSONUtil.toBean(ex.getBodyString(), ApiResponse.class);
        assertTrue(body.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertTrue(data.containsKey("plainRows"));
        assertTrue(data.containsKey("cipherRows"));
        assertTrue(data.containsKey("sqlMeta"));
    }

    private PlaygroundExchange exchange(String method, String path, String body) {
        PlaygroundExchange ex = new PlaygroundExchange();
        ex.setMethod(method);
        ex.setPathInfo(path);
        ex.setBody(body);
        ex.setParams(new HashMap<String, String>());
        return ex;
    }
}
