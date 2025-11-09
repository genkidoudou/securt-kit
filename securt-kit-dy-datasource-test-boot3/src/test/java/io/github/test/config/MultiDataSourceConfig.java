//package io.github.test.config;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Primary;
//
//import javax.sql.DataSource;
//
///**
// * 多数据源测试配置类
// *
// * <p>配置三个数据源用于测试多数据源场景：</p>
// * <ul>
// *   <li>primary: 主数据源，加密 name 和 phone 字段</li>
// *   <li>secondary: 从数据源，只加密 name 字段</li>
// *   <li>third: 第三方数据源，关闭加密功能</li>
// * </ul>
// *
// * @author hexlodev
// * @since 1.1.0
// */
//@TestConfiguration
//public class MultiDataSourceConfig {
//
//    /**
//     * 主数据源（primary）
//     * 在 URL 中添加 datasource-id=primary 参数
//     */
//    @Bean("primaryDataSource")
//    @Primary
//    public DataSource primaryDataSource() {
//        HikariDataSource dataSource = new HikariDataSource();
//        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
//        // 注意：URL 中包含 datasource-id 参数
//        dataSource.setJdbcUrl("jdbc:interceptor:h2:mem:primary_db;DB_CLOSE_DELAY=-1;MODE=MySQL?datasource-id=primary");
//        dataSource.setUsername("sa");
//        dataSource.setPassword("");
//        dataSource.setMaximumPoolSize(5);
//        dataSource.setMinimumIdle(1);
//        return dataSource;
//    }
//
//    /**
//     * 从数据源（secondary）
//     * 在 URL 中添加 datasource-id=secondary 参数
//     */
//    @Bean("secondaryDataSource")
//    public DataSource secondaryDataSource() {
//        HikariDataSource dataSource = new HikariDataSource();
//        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
//        // 注意：URL 中包含 datasource-id 参数
//        dataSource.setJdbcUrl("jdbc:interceptor:h2:mem:secondary_db;DB_CLOSE_DELAY=-1;MODE=MySQL?datasource-id=secondary");
//        dataSource.setUsername("sa");
//        dataSource.setPassword("");
//        dataSource.setMaximumPoolSize(5);
//        dataSource.setMinimumIdle(1);
//        return dataSource;
//    }
//
//    /**
//     * 第三方数据源（third）
//     * 在 URL 中添加 datasource-id=third 参数
//     */
//    @Bean("thirdDataSource")
//    public DataSource thirdDataSource() {
//        HikariDataSource dataSource = new HikariDataSource();
//        dataSource.setDriverClassName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
//        // 注意：URL 中包含 datasource-id 参数
//        dataSource.setJdbcUrl("jdbc:interceptor:h2:mem:third_db;DB_CLOSE_DELAY=-1;MODE=MySQL?datasource-id=third");
//        dataSource.setUsername("sa");
//        dataSource.setPassword("");
//        dataSource.setMaximumPoolSize(5);
//        dataSource.setMinimumIdle(1);
//        return dataSource;
//    }
//}
//
