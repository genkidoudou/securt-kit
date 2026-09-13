package io.github.genkidoudou.core.interceptor;

import lombok.extern.slf4j.Slf4j;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * 简化的JDBC拦截演示程序
 * 展示拦截驱动的基本功能，不依赖外部数据库驱动
 */
@Slf4j
public class SimpleInterceptorDemo {
    
    public static void main(String[] args) {
        log.info("=== Simple JDBC Interceptor Demo ===");
        
        try {
            // 演示拦截驱动注册
            demonstrateDriverRegistration();
            
            // 演示URL拦截
            demonstrateUrlInterception();
            
            // 演示驱动查找
            demonstrateDriverLookup();
            
            log.info("=== Demo Complete ===");
            
        } catch (Exception e) {
            log.error("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 演示驱动注册
     */
    private static void demonstrateDriverRegistration() {
        log.info("\n--- Driver Registration Demo ---");
        
        try {
            // 检查拦截驱动是否已注册
            boolean found = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                if (driver instanceof SimpleInterceptorDriver) {
                    log.info("SUCCESS: SimpleInterceptorDriver is registered");
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                log.warn("FAILED: InterceptorDriver not found in registered drivers");
            }
            
        } catch (Exception e) {
            log.error("Driver registration demo failed: " + e.getMessage());
        }
    }
    
    /**
     * 演示URL拦截
     */
    private static void demonstrateUrlInterception() {
        log.info("\n--- URL Interception Demo ---");
        
        try {
            // 创建拦截驱动实例
            SimpleInterceptorDriver interceptorDriver = new SimpleInterceptorDriver();
            
            // 测试URL接受
            String interceptorUrl = "jdbc:interceptor:h2:mem:testdb";
            String normalUrl = "jdbc:h2:mem:testdb";
            
            boolean acceptsInterceptorUrl = interceptorDriver.acceptsURL(interceptorUrl);
            boolean acceptsNormalUrl = interceptorDriver.acceptsURL(normalUrl);
            
            log.info("Interceptor URL: " + interceptorUrl);
            log.info("Accepts interceptor URL: " + acceptsInterceptorUrl);
            log.info("Normal URL: " + normalUrl);
            log.info("Accepts normal URL: " + acceptsNormalUrl);
            
            if (acceptsInterceptorUrl && !acceptsNormalUrl) {
                log.info("SUCCESS: URL interception works correctly");
            } else {
                log.warn("FAILED: URL interception not working correctly");
            }
            
            // 测试URL提取
            String extractedUrl = interceptorUrl.substring("jdbc:interceptor:".length());
            log.info("Extracted URL: " + extractedUrl);
            
            if (extractedUrl.equals("h2:mem:testdb")) {
                log.info("SUCCESS: URL extraction works correctly");
            } else {
                log.warn("FAILED: URL extraction not working correctly");
            }
            
        } catch (Exception e) {
            log.error("URL interception demo failed: " + e.getMessage());
        }
    }
    
    /**
     * 演示驱动查找
     */
    private static void demonstrateDriverLookup() {
        log.info("\n--- Driver Lookup Demo ---");
        
        try {
            // 列出所有已注册的驱动
            log.info("Registered drivers:");
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            int count = 0;
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                count++;
                String driverClass = driver.getClass().getSimpleName();
                log.info("  " + count + ". " + driverClass);
                
                // 测试每个驱动接受的URL
                if (driver.acceptsURL("jdbc:h2:mem:test")) {
                    log.info("    -> Accepts H2 URLs");
                }
                if (driver.acceptsURL("jdbc:mysql://localhost/test")) {
                    log.info("    -> Accepts MySQL URLs");
                }
                if (driver.acceptsURL("jdbc:interceptor:h2:mem:test")) {
                    log.info("    -> Accepts Interceptor URLs");
                }
            }
            
            log.info("Total registered drivers: " + count);
            
        } catch (Exception e) {
            log.error("Driver lookup demo failed: " + e.getMessage());
        }
    }
}
