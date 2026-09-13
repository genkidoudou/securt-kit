package io.github.test.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

/**
 * 数据库初始化器
 * 
 * <p>在应用启动时自动为所有数据源创建表结构。</p>
 * 
 * <p><b>功能说明：</b></p>
 * <ul>
 *   <li>实现 <code>CommandLineRunner</code> 接口，在应用启动后执行</li>
 *   <li>使用 <code>@Order(1)</code> 确保在其他组件之前执行</li>
 *   <li>自动检测是否为多数据源（<code>DynamicRoutingDataSource</code>）</li>
 *   <li>为每个数据源创建 <code>user</code> 和 <code>order</code> 表</li>
 *   <li>如果表已存在，则跳过创建（使用 <code>CREATE TABLE IF NOT EXISTS</code>）</li>
 * </ul>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>H2 数据库中 <code>user</code> 和 <code>order</code> 都是保留关键字，需要用双引号括起来</li>
 *   <li>使用 <code>AUTO_INCREMENT</code> 作为主键自增方式（H2 也支持 <code>IDENTITY</code>）</li>
 *   <li>如果某个数据源初始化失败，不会影响其他数据源和应用的启动</li>
 * </ul>
 * 
 * <p><b>执行流程：</b></p>
 * <ol>
 *   <li>应用启动后，Spring 容器初始化完成</li>
 *   <li>执行 <code>run()</code> 方法</li>
 *   <li>检测数据源类型（多数据源或单数据源）</li>
 *   <li>为每个数据源创建表结构</li>
 *   <li>输出初始化结果日志</li>
 * </ol>
 * 
 * <p><b>表结构说明：</b></p>
 * <ul>
 *   <li><b>user 表</b>：用户表，包含 id、name、phone、age、email 字段</li>
 *   <li><b>order 表</b>：订单表，包含 id、user_id、order_no、amount、address、status 字段</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 * @see org.springframework.boot.CommandLineRunner
 * @see com.baomidou.dynamic.datasource.DynamicRoutingDataSource
 */
@Component
@Order(1)
public class DatabaseInitializer implements CommandLineRunner {

    /**
     * 数据源（可能是单数据源或多数据源）
     */
    @Autowired
    private DataSource dataSource;

    /**
     * 应用启动后执行的方法
     * 
     * <p>为所有数据源创建表结构。</p>
     * 
     * @param args 命令行参数
     * @throws Exception 如果初始化过程中发生异常
     */
    @Override
    public void run(String... args) throws Exception {
        // H2 数据库中，user 和 order 都是保留关键字，需要用双引号括起来
        // 使用 IDENTITY 或 AUTO_INCREMENT 都可以，但 H2 推荐使用 IDENTITY
        
        // 创建 user 表
        // 表结构：id（主键，自增）、name（姓名，加密字段）、phone（手机号，加密字段）、age（年龄）、email（邮箱）
        String createUserTableSql = "CREATE TABLE IF NOT EXISTS \"user\" (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(100), " +
                "phone VARCHAR(50), " +
                "age INT, " +
                "email VARCHAR(100)" +
                ")";
        
        // 创建 order 表
        // 表结构：id（主键，自增）、user_id（用户ID，外键）、order_no（订单编号）、amount（金额）、address（地址）、status（状态）
        String createOrderTableSql = "CREATE TABLE IF NOT EXISTS \"order\" (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "user_id BIGINT, " +
                "order_no VARCHAR(50), " +
                "amount VARCHAR(20), " +
                "address VARCHAR(200), " +
                "status VARCHAR(20)" +
                ")";
        String createDigestUserTableSql = "CREATE TABLE IF NOT EXISTS digest_user (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255), phone VARCHAR(255), age INT, email VARCHAR(255), " +
                "row_digest VARCHAR(255))";
        String createPlaygroundPersonTableSql = "CREATE TABLE IF NOT EXISTS playground_person (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255), phone VARCHAR(255), id_card VARCHAR(255), age INT, " +
                "row_digest VARCHAR(255))";

        // 为所有数据源创建表
        if (dataSource instanceof DynamicRoutingDataSource) {
            // 多数据源场景：为每个数据源创建表
            DynamicRoutingDataSource dynamicDataSource = (DynamicRoutingDataSource) dataSource;
            Map<String, DataSource> dataSources = dynamicDataSource.getDataSources();
            
            System.out.println("开始初始化数据库表，共 " + dataSources.size() + " 个数据源");
            
            for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                String datasourceName = entry.getKey();
                DataSource ds = entry.getValue();
                
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {
                    // 为当前数据源创建 user 表和 order 表
                    stmt.execute(createUserTableSql);
                    stmt.execute(createOrderTableSql);
                    stmt.execute(createDigestUserTableSql);
                    stmt.execute(createPlaygroundPersonTableSql);
                    System.out.println("数据源 [" + datasourceName + "] 表初始化完成");
                } catch (Exception e) {
                    // 如果某个数据源初始化失败，记录错误但不影响其他数据源
                    System.err.println("数据源 [" + datasourceName + "] 表初始化失败: " + e.getMessage());
                    // 不抛出异常，允许应用继续启动
                }
            }
            
            System.out.println("所有数据源表初始化完成");
        } else {
            // 单数据源场景：只初始化默认数据源
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createUserTableSql);
                stmt.execute(createOrderTableSql);
                stmt.execute(createDigestUserTableSql);
                stmt.execute(createPlaygroundPersonTableSql);
                System.out.println("数据库表初始化完成");
            } catch (Exception e) {
                System.err.println("数据库表初始化失败: " + e.getMessage());
            }
        }
    }
}

