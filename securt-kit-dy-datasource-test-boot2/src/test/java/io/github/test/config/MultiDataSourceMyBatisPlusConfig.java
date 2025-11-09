//package io.github.test.config;
//
//import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
//import com.baomidou.mybatisplus.annotation.DbType;
//import com.baomidou.mybatisplus.core.MybatisConfiguration;
//import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
//import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
//import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
//import org.apache.ibatis.session.SqlSessionFactory;
//import org.mybatis.spring.annotation.MapperScan;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//
//import javax.sql.DataSource;
//
///**
// * MyBatis-Plus 多数据源配置类
// *
// * <p>使用 dynamic-datasource-spring-boot-starter 实现多数据源支持</p>
// *
// * <p>配置说明：</p>
// * <ul>
// *   <li>使用 dynamic-datasource 的自动配置</li>
// *   <li>数据源配置在 application-multi-datasource.yml 中</li>
// *   <li>通过 @DS 注解切换数据源</li>
// *   <li>每个数据源 URL 包含 datasource-id 参数，用于标识数据源</li>
// * </ul>
// *
// * <p>使用示例：</p>
// * <pre>{@code
// * @Service
// * public class UserService {
// *
// *     @DS("primary")  // 使用主数据源
// *     public void saveToPrimary(User user) {
// *         // 使用主数据源执行操作
// *     }
// *
// *     @DS("secondary")  // 使用从数据源
// *     public void queryFromSecondary() {
// *         // 使用从数据源执行查询
// *     }
// * }
// * }</pre>
// *
// * @author hexlodev
// * @since 1.1.0
// * @see com.baomidou.dynamic.datasource.annotation.DS
// */
//@Configuration
//@MapperScan(basePackages = "io.github.test.mapper")
//public class MultiDataSourceMyBatisPlusConfig {
//
//    /**
//     * 配置 MyBatis-Plus 拦截器（分页插件等）
//     */
//    @Bean
//    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        // 添加分页插件
//        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
//        return interceptor;
//    }
//
//    /**
//     * 配置 SqlSessionFactory（支持 MyBatis-Plus 和多数据源）
//     *
//     * <p>dynamic-datasource 会自动创建数据源，这里需要手动配置 SqlSessionFactory
//     * 以支持 MyBatis-Plus 的功能。</p>
//     *
//     * @param dataSource 动态数据源
//     * @return SqlSessionFactory
//     */
//    @Bean
//    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
//        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
//        factoryBean.setDataSource(dataSource);
//
//        // 设置 MyBatis 配置
//        MybatisConfiguration configuration = new MybatisConfiguration();
//        configuration.setMapUnderscoreToCamelCase(true);
//        configuration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
//        factoryBean.setConfiguration(configuration);
//
//        // 添加 MyBatis-Plus 拦截器
//        factoryBean.setPlugins(mybatisPlusInterceptor());
//
//        // 设置 Mapper XML 位置
//        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
//        factoryBean.setMapperLocations(resolver.getResources("classpath:mapper/*.xml"));
//
//        return factoryBean.getObject();
//    }
//
//    /**
//     * 获取动态数据源（用于手动操作）
//     *
//     * <p>注意：dynamic-datasource 会自动配置 DynamicRoutingDataSource，
//     * 这里只是提供一个获取方式，用于测试场景。</p>
//     *
//     * @param dataSource Spring 自动注入的动态数据源
//     * @return 动态路由数据源
//     */
//    @Bean
//    public DynamicRoutingDataSource dynamicRoutingDataSource(DataSource dataSource) {
//        if (dataSource instanceof DynamicRoutingDataSource) {
//            return (DynamicRoutingDataSource) dataSource;
//        }
//        throw new IllegalStateException("DataSource is not DynamicRoutingDataSource");
//    }
//}
//
