package io.github.hexlodev.ui.monitor.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 * 注册安全过滤器和配置
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "securtkit.monitor", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SecurityConfig {

    /**
     * 注册安全头过滤器
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/monitor/*");
        registration.setName("securityHeadersFilter");
        registration.setOrder(1);
        return registration;
    }
}

