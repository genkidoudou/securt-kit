package io.github.test;

import com.baomidou.dynamic.datasource.annotation.DS;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.UserEntityMapper;
import io.github.test.service.MultiDataSourceUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * MyBatis-Plus 多数据源使用示例
 * 
 * <p>演示如何在 Spring Boot 应用中使用 MyBatis-Plus 多数据源功能</p>
 * 
 * <p>关键点：</p>
 * <ul>
 *   <li>使用 @DS 注解切换数据源</li>
 *   <li>数据源 URL 必须包含 datasource-id 参数</li>
 *   <li>不同数据源可以配置不同的加密策略</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
    "io.github.test",
    "io.github.genkidoudou",
    "com.baomidou.dynamic.datasource"
})
public class MultiDataSourceMyBatisPlusExample implements CommandLineRunner {

    @Autowired
    private MultiDataSourceUserService userService;

    @Autowired
    private UserEntityMapper userEntityMapper;

    public static void main(String[] args) {
        SpringApplication.run(MultiDataSourceMyBatisPlusExample.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("=== MyBatis-Plus 多数据源使用示例 ===");

        // 示例1：在主数据源中保存用户（加密 name 和 phone）
        UserEntity primaryUser = new UserEntity();
        primaryUser.setName("主数据源用户");
        primaryUser.setPhone("13800138000");
        primaryUser.setEmail("primary@example.com");
        
        int result1 = userService.saveToPrimary(primaryUser);
        System.out.println("主数据源插入结果: " + result1);

        // 示例2：在从数据源中保存用户（只加密 name）
        UserEntity secondaryUser = new UserEntity();
        secondaryUser.setName("从数据源用户");
        secondaryUser.setPhone("13900139000");
        secondaryUser.setEmail("secondary@example.com");
        
        int result2 = userService.saveToSecondary(secondaryUser);
        System.out.println("从数据源插入结果: " + result2);

        // 示例3：在第三方数据源中保存用户（不加密）
        UserEntity thirdUser = new UserEntity();
        thirdUser.setName("第三方数据源用户");
        thirdUser.setPhone("13700137000");
        thirdUser.setEmail("third@example.com");
        
        int result3 = userService.saveToThird(thirdUser);
        System.out.println("第三方数据源插入结果: " + result3);

        // 示例4：直接使用 Mapper 时指定数据源
        System.out.println("\n=== 使用 Mapper 直接操作 ===");
        useMapperWithDS();
    }

    /**
     * 演示在 Mapper 方法上使用 @DS 注解
     */
    @DS("primary")
    private void useMapperWithDS() {
        UserEntity user = new UserEntity();
        user.setName("Mapper测试用户");
        user.setPhone("13600136000");
        
        int result = userEntityMapper.insert(user);
        System.out.println("使用 @DS 注解的 Mapper 插入结果: " + result);

        UserEntity found = userEntityMapper.selectById(user.getId());
        System.out.println("查询结果: " + found.getName() + ", " + found.getPhone());
    }
}

