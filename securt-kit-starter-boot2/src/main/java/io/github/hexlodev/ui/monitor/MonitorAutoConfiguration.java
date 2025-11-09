package io.github.hexlodev.ui.monitor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 监控页面自动配置
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "securtkit.monitor", 
                      name = "enabled", 
                      havingValue = "true",
                      matchIfMissing = false)
@ComponentScan("io.github.hexlodev.ui")
@EnableConfigurationProperties(MonitorProperties.class)
public class MonitorAutoConfiguration implements WebMvcConfigurer {
    
    private final MonitorProperties properties;

    public MonitorAutoConfiguration(MonitorProperties properties) {
        this.properties = properties;
    }

    /**
     * 配置静态资源处理
     * Spring Boot 会自动处理 static 目录，但这里明确配置以确保正确访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = properties.getPath();
        registry.addResourceHandler(path + "/**")
                .addResourceLocations("classpath:/static" + path + "/");
    }
}


