package io.github.hexlodev.ui.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控页面配置属性
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "securtkit.monitor")
public class MonitorProperties {
    /**
     * 是否启用监控页面
     */
    private boolean enabled = true;

    /**
     * 监控页面用户名
     */
    private String username = "admin";

    /**
     * 监控页面密码
     */
    private String password = "admin123";

    /**
     * 监控页面访问路径前缀
     */
    private String path = "/monitor";
}

