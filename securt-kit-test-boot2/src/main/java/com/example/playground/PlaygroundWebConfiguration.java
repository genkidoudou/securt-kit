package com.example.playground;

import com.example.mapper.PlaygroundScenarioMapper;
import com.example.mapper.UserEntityMapper;
import io.github.genkidoudou.playground.PlaygroundDataSourceLocator;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.PlaygroundProperties;
import io.github.genkidoudou.playground.PlaygroundScenarioRunner;
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
 * 仅测试工程启用的 Playground 装配（不进入 securt-kit-starter）。
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "securtkit.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(PlaygroundProperties.class)
public class PlaygroundWebConfiguration {

    @Bean
    public PlaygroundEngine playgroundEngine(PlaygroundProperties playgroundProperties,
                                             @Autowired(required = false) DataSource dataSource,
                                             @Autowired(required = false) PlaygroundDataSourceLocator locator,
                                             @Autowired(required = false) UserEntityMapper userMapper,
                                             @Autowired(required = false) PlaygroundScenarioMapper scenarioMapper) {
        PlaygroundScenarioRunner runner = null;
        if (userMapper != null && scenarioMapper != null && dataSource != null) {
            runner = new MpPlaygroundScenarioRunner(userMapper, scenarioMapper, dataSource);
        }
        return new PlaygroundEngine(playgroundProperties, dataSource, locator, runner);
    }

    @Bean
    public PlaygroundDispatcher playgroundDispatcher(PlaygroundEngine playgroundEngine,
                                                     PlaygroundProperties playgroundProperties) {
        return new PlaygroundDispatcher(playgroundEngine, playgroundProperties);
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ServletRegistrationBean playgroundStatViewServlet(
            PlaygroundDispatcher playgroundDispatcher, PlaygroundProperties playgroundProperties) {
        String path = playgroundProperties.getPath();
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
                new ServletRegistrationBean(new PlaygroundStatViewServlet(playgroundDispatcher, playgroundProperties));
        bean.setName("securtKitPlaygroundStatViewServlet");
        bean.addUrlMappings(path, path + "/*");
        bean.setLoadOnStartup(3);
        return bean;
    }
}
