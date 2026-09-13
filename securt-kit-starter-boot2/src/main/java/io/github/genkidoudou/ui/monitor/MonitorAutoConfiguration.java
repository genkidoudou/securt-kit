package io.github.genkidoudou.ui.monitor;

import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.monitor.MonitorEngine;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.support.MonitorDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 监控页面自动配置（Boot2）
 *
 * <p>以 Druid {@code StatViewServlet} 相同方式注册 Servlet，
 * 静态资源与 API 均由 {@link MonitorStatViewServlet} 统一处理。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "securtkit.monitor", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({MonitorProperties.class, FieldEncryptorProperties.class})
public class MonitorAutoConfiguration {

    @Bean
    public MonitorEngine monitorEngine(MonitorProperties monitorProperties,
                                       FieldEncryptorProperties fieldEncryptorProperties,
                                       @Autowired(required = false) DataSource dataSource) {
        return new MonitorEngine(monitorProperties, fieldEncryptorProperties, dataSource);
    }

    @Bean
    public MonitorDispatcher monitorDispatcher(MonitorEngine monitorEngine, MonitorProperties monitorProperties) {
        return new MonitorDispatcher(monitorEngine, monitorProperties);
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ServletRegistrationBean monitorStatViewServlet(
            MonitorDispatcher monitorDispatcher, MonitorProperties monitorProperties) {
        String path = monitorProperties.getPath();
        if (path == null || path.trim().isEmpty()) {
            path = "/monitor";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        ServletRegistrationBean bean =
                new ServletRegistrationBean(new MonitorStatViewServlet(monitorDispatcher, monitorProperties));
        bean.setName("securtKitMonitorStatViewServlet");
        bean.addUrlMappings(path, path + "/*");
        bean.setLoadOnStartup(2);
        return bean;
    }
}
