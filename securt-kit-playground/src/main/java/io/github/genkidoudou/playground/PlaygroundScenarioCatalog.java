package io.github.genkidoudou.playground;

import io.github.genkidoudou.playground.dto.ScenarioDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed scenario catalog used when no host runner is registered,
 * and as the baseline id set for availability overlays.
 */
public final class PlaygroundScenarioCatalog {

    private PlaygroundScenarioCatalog() {
    }

    public static List<ScenarioDescriptor> baseline() {
        List<ScenarioDescriptor> list = new ArrayList<>();
        list.add(ScenarioDescriptor.of("single-eq", "单表等值",
                "按 user.phone 等值查询",
                Collections.singletonList(
                        new ScenarioDescriptor.ParamSchema("phone", "手机号", true, "明文手机号"))));
        list.add(ScenarioDescriptor.of("join-user-orders", "用户订单联查",
                "user ⋈ orders（表别名）",
                Arrays.asList(
                        new ScenarioDescriptor.ParamSchema("phone", "用户手机号", false, "可选过滤"),
                        new ScenarioDescriptor.ParamSchema("userId", "用户 ID", false, "可选"))));
        list.add(ScenarioDescriptor.of("column-alias", "列别名",
                "SELECT phone AS mobile 等",
                Collections.singletonList(
                        new ScenarioDescriptor.ParamSchema("phone", "手机号", true, "明文手机号"))));
        list.add(ScenarioDescriptor.of("table-alias", "表别名",
                "FROM \"user\" u WHERE u.phone = ?",
                Collections.singletonList(
                        new ScenarioDescriptor.ParamSchema("phone", "手机号", true, "明文手机号"))));
        list.add(ScenarioDescriptor.of("like-phone", "加密列 LIKE",
                "对 user.phone 执行 LIKE（默认 ExactMatchLikePatternHandler）",
                Collections.singletonList(
                        new ScenarioDescriptor.ParamSchema("pattern", "匹配模式", true, "精确值或含 % 的模式"))));
        list.add(ScenarioDescriptor.of("func-on-cipher", "加密列函数",
                "UPPER/CONCAT 等函数作用在密文列上的能力边界演示",
                Collections.singletonList(
                        new ScenarioDescriptor.ParamSchema("phone", "手机号片段", false, "用于 UPPER 对比"))));
        return list;
    }
}
