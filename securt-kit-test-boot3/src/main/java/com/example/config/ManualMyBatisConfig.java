//package com.example.config;
//
//import com.baomidou.mybatisplus.annotation.DbType;
//import com.baomidou.mybatisplus.core.MybatisConfiguration;
//import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
//import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
//import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
//import org.apache.ibatis.logging.stdout.StdOutImpl;
//import org.apache.ibatis.session.SqlSessionFactory;
//import org.mybatis.spring.annotation.MapperScan;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.jdbc.datasource.DriverManagerDataSource;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.annotation.EnableTransactionManagement;
//
//import javax.sql.DataSource;
//
///**
// * 手动配置 MyBatis 和 MyBatis-Plus
// * 所有配置都从 application.yml 读取
// *
// * @author hexlodev
// * @since 1.0.0
// */
//@Configuration
//@EnableTransactionManagement
//@MapperScan(basePackages = "io.github.test.mapper")
//public class ManualMyBatisConfig {
//
//    @Value("${spring.datasource.driver-class-name}")
//    private String driverClassName;
//
//    @Value("${spring.datasource.url}")
//    private String url;
//
//    @Value("${spring.datasource.username}")
//    private String username;
//
//    @Value("${spring.datasource.password}")
//    private String password;
//
//    @Value("${mybatis.mapper-locations:classpath:mapper/*.xml}")
//    private String mapperLocations;
//
//    @Value("${mybatis.type-aliases-package:io.github.test.entity}")
//    private String typeAliasesPackage;
//
//    @Value("${mybatis.config-location:}")
//    private String configLocation;
//
//    /**
//     * 手动配置数据源
//     */
//    @Bean
//    @Primary
//    public DataSource dataSource() {
//        DriverManagerDataSource dataSource = new DriverManagerDataSource();
//        dataSource.setDriverClassName(driverClassName);
//        dataSource.setUrl(url);
//        dataSource.setUsername(username);
//        dataSource.setPassword(password);
//        return dataSource;
//    }
//
//    /**
//     * 手动配置 MyBatis-Plus 拦截器
//     */
//    @Bean
//    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        // 添加分页插件
//        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
//        paginationInnerInterceptor.setDbType(DbType.H2);
//        paginationInnerInterceptor.setMaxLimit(100L);
//        interceptor.addInnerInterceptor(paginationInnerInterceptor);
//        return interceptor;
//    }
//
//    /**
//     * 手动配置 SqlSessionFactory
//     * 从 application.yml 读取所有配置
//     */
//    @Bean
//    @Primary
//    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
//        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
//        factoryBean.setDataSource(dataSource);
//
//        // 设置 MyBatis 配置
//        MybatisConfiguration configuration = new MybatisConfiguration();
//        // 从 application.yml 读取的配置
//        configuration.setMapUnderscoreToCamelCase(true);
//        configuration.setLogImpl(StdOutImpl.class);
//        configuration.setCacheEnabled(true);
//        configuration.setLazyLoadingEnabled(false);
//        configuration.setAggressiveLazyLoading(false);
//        configuration.setMultipleResultSetsEnabled(true);
//        configuration.setUseColumnLabel(true);
//        configuration.setUseGeneratedKeys(true);
//        configuration.setDefaultExecutorType(org.apache.ibatis.session.ExecutorType.REUSE);
//        configuration.setDefaultStatementTimeout(25);
//        configuration.setAutoMappingBehavior(org.apache.ibatis.session.AutoMappingBehavior.PARTIAL);
//        configuration.setAutoMappingUnknownColumnBehavior(
//            org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.WARNING
//        );
//
//        factoryBean.setConfiguration(configuration);
//
//        // 设置 MyBatis 配置文件（如果指定）
//        if (configLocation != null && !configLocation.isEmpty()) {
//            Resource configResource = new PathMatchingResourcePatternResolver()
//                .getResource(configLocation);
//            factoryBean.setConfigLocation(configResource);
//        }
//
//        // 设置 Mapper XML 文件位置
//        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
//        Resource[] mapperResources = resolver.getResources(mapperLocations);
//        factoryBean.setMapperLocations(mapperResources);
//
//        // 设置类型别名包
//        factoryBean.setTypeAliasesPackage(typeAliasesPackage);
//
//        // 添加 MyBatis-Plus 拦截器
//        factoryBean.setPlugins(mybatisPlusInterceptor());
//
//        return factoryBean.getObject();
//    }
//
//    /**
//     * 配置事务管理器
//     */
//    @Bean
//    public PlatformTransactionManager transactionManager(DataSource dataSource) {
//        return new DataSourceTransactionManager(dataSource);
//    }
//}
//
