package io.github.test;

import cn.hutool.db.ds.simple.SimpleDataSource;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

@SpringBootConfiguration
@ComponentScan(basePackages = {
        "io.github",
        "cn.hutool.extra.spring"
})
public class TestApplication {

    /**
     * 配置 DataSource（使用拦截器驱动）
     */
    @Bean
    public DataSource dataSource() {
        String dbUrl = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
        // 加载拦截器驱动
        try {
            Class.forName("io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SimpleInterceptorDriver", e);
        }
        return new SimpleDataSource(dbUrl, "sa", "", "io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver");
    }
}
