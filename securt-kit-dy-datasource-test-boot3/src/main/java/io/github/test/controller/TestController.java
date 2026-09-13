package io.github.test.controller;

import io.github.test.entity.UserEntity;
import io.github.test.service.MultiDataSourceUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多数据源测试控制器
 * 
 * <p>提供 REST API 用于测试多数据源的加密/解密功能</p>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private MultiDataSourceUserService userService;

    /**
     * 测试主数据源（加密 name 和 phone）
     */
    @GetMapping("/primary")
    public Map<String, Object> testPrimary() {
        Map<String, Object> result = new HashMap<>();
        
        // 创建测试用户
        UserEntity user = new UserEntity();
        user.setName("张三");
        user.setPhone("13800138000");
        user.setAge(25);
        user.setEmail("zhangsan@example.com");
        
        // 保存到主数据源
        int insertResult = userService.saveToPrimary(user);
        result.put("insertResult", insertResult);
        result.put("insertedUser", user);
        
        // 查询用户（应该自动解密）
        UserEntity queriedUser = userService.getFromPrimary(user.getId());
        result.put("queriedUser", queriedUser);

        // 查询所有用户
        List<UserEntity> allUsers = userService.listFromPrimary();
        result.put("allUsers", allUsers);
        result.put("totalCount", allUsers.size());

        result.put("message", "主数据源测试完成（name 和 phone 字段应该被加密）");
        return result;
    }

    /**
     * 测试从数据源（只加密 name）
     */
    @GetMapping("/secondary")
    public Map<String, Object> testSecondary() {
        Map<String, Object> result = new HashMap<>();
        
        // 创建测试用户
        UserEntity user = new UserEntity();
        user.setName("李四");
        user.setPhone("13900139000");
        user.setAge(30);
        user.setEmail("lisi@example.com");
        
        // 保存到从数据源
        int insertResult = userService.saveToSecondary(user);
        result.put("insertResult", insertResult);
        result.put("insertedUser", user);
        
        // 查询用户（name 应该被解密，phone 保持原样）
        UserEntity queriedUser = userService.getFromSecondary(user.getId());
        result.put("queriedUser", queriedUser);
        
        // 查询所有用户
        List<UserEntity> allUsers = userService.listFromSecondary();
        result.put("allUsers", allUsers);
        result.put("totalCount", allUsers.size());
        
        result.put("message", "从数据源测试完成（只有 name 字段应该被加密）");
        return result;
    }

    /**
     * 测试第三方数据源（不加密）
     */
    @GetMapping("/third")
    public Map<String, Object> testThird() {
        Map<String, Object> result = new HashMap<>();
        
        // 创建测试用户
        UserEntity user = new UserEntity();
        user.setName("王五");
        user.setPhone("13700137000");
        user.setAge(28);
        user.setEmail("wangwu@example.com");
        
        // 保存到第三方数据源
        int insertResult = userService.saveToThird(user);
        result.put("insertResult", insertResult);
        result.put("insertedUser", user);
        
        // 查询用户（所有字段保持原样）
        UserEntity queriedUser = userService.getFromThird(user.getId());
        result.put("queriedUser", queriedUser);
        
        // 查询所有用户
        List<UserEntity> allUsers = userService.listFromThird();
        result.put("allUsers", allUsers);
        result.put("totalCount", allUsers.size());
        
        result.put("message", "第三方数据源测试完成（所有字段都不加密）");
        return result;
    }

    /**
     * 测试所有数据源
     */
    @GetMapping("/all")
    public Map<String, Object> testAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("primary", testPrimary());
        result.put("secondary", testSecondary());
        result.put("third", testThird());
        result.put("message", "所有数据源测试完成");
        return result;
    }
}

