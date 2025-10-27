package io.github.hexlodev.core.interceptor;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * 简化的JDBC拦截演示程序
 * 展示拦截驱动的基本功能，不依赖外部数据库驱动
 */
public class SimpleInterceptorDemo {
    
    private static final Logger logger = Logger.getLogger(SimpleInterceptorDemo.class.getName());
    
    public static void main(String[] args) {
        logger.info("=== Simple JDBC Interceptor Demo ===");
        
        try {
            // 演示拦截驱动注册
            demonstrateDriverRegistration();
            
            // 演示URL拦截
            demonstrateUrlInterception();
            
            // 演示驱动查找
            demonstrateDriverLookup();
            
            logger.info("=== Demo Complete ===");
            
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 演示驱动注册
     */
    private static void demonstrateDriverRegistration() {
        logger.info("\n--- Driver Registration Demo ---");
        
        try {
            // 检查拦截驱动是否已注册
            boolean found = false;
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                if (driver instanceof SimpleInterceptorDriver) {
                    logger.info("SUCCESS: SimpleInterceptorDriver is registered");
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                logger.warning("FAILED: InterceptorDriver not found in registered drivers");
            }
            
        } catch (Exception e) {
            logger.severe("Driver registration demo failed: " + e.getMessage());
        }
    }
    
    /**
     * 演示URL拦截
     */
    private static void demonstrateUrlInterception() {
        logger.info("\n--- URL Interception Demo ---");
        
        try {
            // 创建拦截驱动实例
            SimpleInterceptorDriver interceptorDriver = new SimpleInterceptorDriver();
            
            // 测试URL接受
            String interceptorUrl = "jdbc:interceptor:h2:mem:testdb";
            String normalUrl = "jdbc:h2:mem:testdb";
            
            boolean acceptsInterceptorUrl = interceptorDriver.acceptsURL(interceptorUrl);
            boolean acceptsNormalUrl = interceptorDriver.acceptsURL(normalUrl);
            
            logger.info("Interceptor URL: " + interceptorUrl);
            logger.info("Accepts interceptor URL: " + acceptsInterceptorUrl);
            logger.info("Normal URL: " + normalUrl);
            logger.info("Accepts normal URL: " + acceptsNormalUrl);
            
            if (acceptsInterceptorUrl && !acceptsNormalUrl) {
                logger.info("SUCCESS: URL interception works correctly");
            } else {
                logger.warning("FAILED: URL interception not working correctly");
            }
            
            // 测试URL提取
            String extractedUrl = interceptorUrl.substring("jdbc:interceptor:".length());
            logger.info("Extracted URL: " + extractedUrl);
            
            if (extractedUrl.equals("h2:mem:testdb")) {
                logger.info("SUCCESS: URL extraction works correctly");
            } else {
                logger.warning("FAILED: URL extraction not working correctly");
            }
            
        } catch (Exception e) {
            logger.severe("URL interception demo failed: " + e.getMessage());
        }
    }
    
    /**
     * 演示驱动查找
     */
    private static void demonstrateDriverLookup() {
        logger.info("\n--- Driver Lookup Demo ---");
        
        try {
            // 列出所有已注册的驱动
            logger.info("Registered drivers:");
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            int count = 0;
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                count++;
                String driverClass = driver.getClass().getSimpleName();
                logger.info("  " + count + ". " + driverClass);
                
                // 测试每个驱动接受的URL
                if (driver.acceptsURL("jdbc:h2:mem:test")) {
                    logger.info("    -> Accepts H2 URLs");
                }
                if (driver.acceptsURL("jdbc:mysql://localhost/test")) {
                    logger.info("    -> Accepts MySQL URLs");
                }
                if (driver.acceptsURL("jdbc:interceptor:h2:mem:test")) {
                    logger.info("    -> Accepts Interceptor URLs");
                }
            }
            
            logger.info("Total registered drivers: " + count);
            
        } catch (Exception e) {
            logger.severe("Driver lookup demo failed: " + e.getMessage());
        }
    }
}
