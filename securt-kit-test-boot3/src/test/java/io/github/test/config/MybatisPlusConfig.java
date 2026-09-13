package io.github.test.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置类（测试环境）
 * <p>
 * 不强制装配分页插件，避免不同 MP 版本对 {@code PaginationInnerInterceptor}
 * 模块拆分导致的编译差异；分页相关用例可按需自行注册。
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Configuration
@MapperScan(basePackages = "io.github.test.mapper")
public class MybatisPlusConfig {

    /**
     * 手动配置 SqlSessionFactory（用于测试环境）
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }
}
