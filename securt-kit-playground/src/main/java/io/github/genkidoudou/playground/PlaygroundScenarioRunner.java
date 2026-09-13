package io.github.genkidoudou.playground;

import io.github.genkidoudou.playground.dto.ScenarioDescriptor;
import io.github.genkidoudou.playground.dto.ScenarioRunResult;

import java.util.List;
import java.util.Map;

/**
 * Host-provided complex-query scenario runner (typically MyBatis-Plus in test apps).
 * Playground module must not depend on MyBatis-Plus at compile time.
 */
public interface PlaygroundScenarioRunner {

    /**
     * @return fixed catalog; availability may still be computed by the engine
     */
    List<ScenarioDescriptor> list();

    /**
     * Execute one scenario.
     *
     * @param scenarioId   catalog id
     * @param params       scenario parameters
     * @param datasourceId selected datasource id (may be null for default)
     * @return plain / cipher / sqlMeta evidence
     */
    ScenarioRunResult run(String scenarioId, Map<String, Object> params, String datasourceId);
}
