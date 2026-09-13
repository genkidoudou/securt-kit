package io.github.test.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import io.github.genkidoudou.playground.PlaygroundDataSourceLocator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为 Playground 暴露 dynamic-datasource 各子数据源。
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(DynamicRoutingDataSource.class)
public class PlaygroundMultiDsConfig {

    @Bean
    public PlaygroundDataSourceLocator playgroundDataSourceLocator(DataSource dataSource) {
        return () -> {
            if (dataSource instanceof DynamicRoutingDataSource) {
                Map<String, DataSource> sources = ((DynamicRoutingDataSource) dataSource).getDataSources();
                return sources == null ? Collections.<String, DataSource>emptyMap() : new LinkedHashMap<>(sources);
            }
            Map<String, DataSource> single = new LinkedHashMap<>();
            single.put("default", dataSource);
            return single;
        };
    }
}
