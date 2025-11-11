package io.github.test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 多数据源测试应用启动类
 * 
 * <p>演示如何使用 securt-kit 在多数据源场景下进行字段加密/解密</p>
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>支持三个数据源：primary、secondary、third</li>
 *   <li>primary 数据源：加密 name 和 phone 字段</li>
 *   <li>secondary 数据源：只加密 name 字段</li>
 *   <li>third 数据源：不加密任何字段</li>
 * </ul>
 * 
 * <p>启动后访问：</p>
 * <ul>
 *   <li>http://localhost:8081/test/primary - 测试主数据源</li>
 *   <li>http://localhost:8081/test/secondary - 测试从数据源</li>
 *   <li>http://localhost:8081/test/third - 测试第三方数据源</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@SpringBootApplication
@MapperScan(basePackages = "io.github.test.mapper")
public class MultiDataSourceApplication2 {

    public static void main(String[] args) {
        SpringApplication.run(MultiDataSourceApplication2.class, args);
        System.out.println("\n========================================");
        System.out.println("多数据源测试应用启动成功！");
        System.out.println("访问地址：");
        System.out.println("  - http://localhost:8081/test/primary");
        System.out.println("  - http://localhost:8081/test/secondary");
        System.out.println("  - http://localhost:8081/test/third");
        System.out.println("========================================\n");
    }
}

