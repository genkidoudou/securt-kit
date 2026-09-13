package io.github.test.playground;

import io.github.genkidoudou.playground.PlaygroundDataSourceLocator;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.PlaygroundProperties;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 多数据源测试工程的 Playground 装配。
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "securtkit.playground", name = "enabled",
        havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(PlaygroundProperties.class)
public class PlaygroundWebConfig {

    @Bean
    public PlaygroundEngine playgroundEngine(
            PlaygroundProperties properties,
            @Autowired(required = false) DataSource dataSource,
            @Autowired(required = false) PlaygroundDataSourceLocator locator) {
        return new PlaygroundEngine(properties, dataSource, locator);
    }

    @Bean
    public PlaygroundDispatcher playgroundDispatcher(
            PlaygroundEngine engine, PlaygroundProperties properties) {
        return new PlaygroundDispatcher(engine, properties);
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ServletRegistrationBean playgroundServlet(
            PlaygroundDispatcher dispatcher, PlaygroundProperties properties) {
        String path = properties.getPath();
        if (path == null || path.trim().isEmpty()) {
            path = "/playground";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        ServletRegistrationBean bean =
                new ServletRegistrationBean(new PlaygroundServlet(dispatcher, properties));
        bean.setName("securtKitPlaygroundStatViewServlet");
        bean.addUrlMappings(path, path + "/*");
        bean.setLoadOnStartup(3);
        return bean;
    }
}
