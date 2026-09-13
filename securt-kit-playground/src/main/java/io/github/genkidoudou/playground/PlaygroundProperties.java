package io.github.genkidoudou.playground;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Playground 演示页配置
 *
 * <p>spring-boot 为 optional 依赖，非 Spring 环境下可直接 new 使用。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "securtkit.playground")
public class PlaygroundProperties {

    /**
     * 是否启用 playground（默认关闭，测试工程显式打开）
     */
    private boolean enabled = false;

    /**
     * 访问路径前缀
     */
    private String path = "/playground";

    /**
     * 是否启用登录鉴权（本地演示可关闭）
     */
    private boolean authEnabled = false;

    /**
     * 登录用户名（仅 authEnabled=true 时有效）
     */
    private String username = "admin";

    /**
     * 登录密码（仅 authEnabled=true 时有效）
     */
    private String password = "admin123";

    /**
     * 允许操作的表白名单（canonical 小写名，不含引号）
     */
    private List<String> allowedTables = new ArrayList<>(Arrays.asList(
            "user", "digest_user", "orders", "playground_person"));
}
