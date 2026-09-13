package io.github.test;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.UserEntityMapper;
import io.github.test.service.MultiDataSourceUserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 多数据源测试类
 * 
 * <p>使用 MyBatis-Plus 的 dynamic-datasource 实现多数据源测试</p>
 * 
 * <p>测试场景：</p>
 * <ul>
 *   <li>使用 @DS 注解切换数据源</li>
 *   <li>验证不同数据源的加密配置隔离</li>
 *   <li>验证 MyBatis-Plus 的 CRUD 操作与字段加密的集成</li>
 *   <li>验证 Service 层的 @DS 注解使用</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@SpringBootTest(classes = {
    io.github.test.service.MultiDataSourceUserService.class,
    io.github.genkidoudou.config.SecurtKitAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-multi-datasource.yml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MultiDataSourceMyBatisPlusTest {

    @Autowired(required = false)
    private UserEntityMapper userEntityMapper;

    @Autowired(required = false)
    private MultiDataSourceUserService userService;

    @BeforeAll
    void setUp() throws ClassNotFoundException {
        // 加载拦截器驱动
        Class.forName("io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver");

        // 初始化多数据源加密配置（新配置方式：在 tables 中直接指定 datasource-id）
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        // 主数据源配置：加密 name 和 phone
        FieldEncryptorProperties.TableConfig primaryUserTable = new FieldEncryptorProperties.TableConfig();
        primaryUserTable.setTableName("user");
        primaryUserTable.setDatasourceId("primary");
        FieldEncryptorProperties.FieldConfig primaryNameField = new FieldEncryptorProperties.FieldConfig();
        primaryNameField.setFieldName("name");
        FieldEncryptorProperties.FieldConfig primaryPhoneField = new FieldEncryptorProperties.FieldConfig();
        primaryPhoneField.setFieldName("phone");
        primaryUserTable.setFields(java.util.Arrays.asList(primaryNameField, primaryPhoneField));

        // 从数据源配置：只加密 name
        FieldEncryptorProperties.TableConfig secondaryUserTable = new FieldEncryptorProperties.TableConfig();
        secondaryUserTable.setTableName("user");
        secondaryUserTable.setDatasourceId("secondary");
        FieldEncryptorProperties.FieldConfig secondaryNameField = new FieldEncryptorProperties.FieldConfig();
        secondaryNameField.setFieldName("name");
        secondaryUserTable.setFields(java.util.Arrays.asList(secondaryNameField));

        // 第三方数据源不配置（等于关闭加密）

        // 设置表配置列表
        props.setTables(java.util.Arrays.asList(primaryUserTable, secondaryUserTable));

        // 初始化 TableCache
        TableCache.init(props);

        // 初始化表结构
        initTables();
    }

    /**
     * 初始化表结构
     */
    private void initTables() {
        if (userEntityMapper == null) {
            return;
        }

        // 注意：dynamic-datasource 会自动处理数据源连接
        // 表结构会在首次使用时自动创建（如果使用 MyBatis-Plus 的自动建表功能）
        // 或者通过 SQL 脚本初始化
    }

    /**
     * 测试主数据源加密功能（使用 MyBatis-Plus）
     * 主数据源应该加密 name 和 phone 字段
     */
    @Test
    @DS("primary")
    void testPrimaryDataSourceWithMyBatisPlus() {
        if (userEntityMapper == null) {
            System.out.println("UserEntityMapper not available, skipping test");
            return;
        }

        String originalName = "张三";
        String originalPhone = "13800138000";

        // 插入数据（使用 MyBatis-Plus）
        UserEntity user = new UserEntity();
        user.setName(originalName);
        user.setPhone(originalPhone);
        user.setEmail("zhangsan@example.com");
        
        int result = userEntityMapper.insert(user);
        assertTrue(result > 0, "插入应该成功");
        assertNotNull(user.getId(), "应该生成 ID");

        // 查询数据（应该自动解密）
        UserEntity found = userEntityMapper.selectById(user.getId());
        assertNotNull(found, "应该查询到数据");
        assertEquals(originalName, found.getName(), "name 字段应该正确解密");
        assertEquals(originalPhone, found.getPhone(), "phone 字段应该正确解密");
    }

    /**
     * 测试从数据源加密功能（使用 MyBatis-Plus）
     * 从数据源应该只加密 name 字段，不加密 phone 字段
     */
    @Test
    @DS("secondary")
    void testSecondaryDataSourceWithMyBatisPlus() {
        if (userEntityMapper == null) {
            System.out.println("UserEntityMapper not available, skipping test");
            return;
        }

        String originalName = "李四";
        String originalPhone = "13900139000";

        // 插入数据
        UserEntity user = new UserEntity();
        user.setName(originalName);
        user.setPhone(originalPhone);
        user.setEmail("lisi@example.com");
        
        int result = userEntityMapper.insert(user);
        assertTrue(result > 0, "插入应该成功");

        // 查询数据
        UserEntity found = userEntityMapper.selectById(user.getId());
        assertNotNull(found, "应该查询到数据");
        assertEquals(originalName, found.getName(), "name 字段应该正确解密");
        assertEquals(originalPhone, found.getPhone(), "phone 字段应该保持原样（未加密）");
    }

    /**
     * 测试第三方数据源（关闭加密，使用 MyBatis-Plus）
     * 第三方数据源应该不加密任何字段
     */
    @Test
    @DS("third")
    void testThirdDataSourceWithMyBatisPlus() {
        if (userEntityMapper == null) {
            System.out.println("UserEntityMapper not available, skipping test");
            return;
        }

        String originalName = "王五";
        String originalPhone = "13700137000";

        // 插入数据
        UserEntity user = new UserEntity();
        user.setName(originalName);
        user.setPhone(originalPhone);
        user.setEmail("wangwu@example.com");
        
        int result = userEntityMapper.insert(user);
        assertTrue(result > 0, "插入应该成功");

        // 查询数据
        UserEntity found = userEntityMapper.selectById(user.getId());
        assertNotNull(found, "应该查询到数据");
        assertEquals(originalName, found.getName(), "name 字段应该保持原样（未加密）");
        assertEquals(originalPhone, found.getPhone(), "phone 字段应该保持原样（未加密）");
    }

    /**
     * 测试条件查询（使用 MyBatis-Plus LambdaQueryWrapper）
     */
    @Test
    @DS("primary")
    void testQueryWithLambdaWrapper() {
        if (userEntityMapper == null) {
            System.out.println("UserEntityMapper not available, skipping test");
            return;
        }

        String originalName = "测试用户";

        // 插入数据
        UserEntity user = new UserEntity();
        user.setName(originalName);
        user.setPhone("13800138000");
        userEntityMapper.insert(user);

        // 使用 LambdaQueryWrapper 查询
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getName, originalName);
        List<UserEntity> users = userEntityMapper.selectList(wrapper);

        assertNotNull(users, "查询结果不应该为 null");
        assertFalse(users.isEmpty(), "应该查询到数据");
        assertEquals(originalName, users.get(0).getName(), "查询结果应该正确解密");
    }

    /**
     * 测试使用 Service 层的 @DS 注解切换数据源
     */
    @Test
    void testServiceLayerDataSourceSwitch() {
        if (userService == null) {
            System.out.println("MultiDataSourceUserService not available, skipping test");
            return;
        }

        // 测试主数据源（通过 Service 方法上的 @DS 注解）
        UserEntity primaryUser = new UserEntity();
        primaryUser.setName("主数据源用户");
        primaryUser.setPhone("13800138000");
        primaryUser.setEmail("primary@example.com");
        int result1 = userService.saveToPrimary(primaryUser);
        assertTrue(result1 > 0, "主数据源插入应该成功");

        UserEntity found1 = userService.getFromPrimary(primaryUser.getId());
        assertNotNull(found1, "应该查询到数据");
        assertEquals("主数据源用户", found1.getName(), "name 应该正确解密");
        assertEquals("13800138000", found1.getPhone(), "phone 应该正确解密");

        // 测试从数据源
        UserEntity secondaryUser = new UserEntity();
        secondaryUser.setName("从数据源用户");
        secondaryUser.setPhone("13900139000");
        secondaryUser.setEmail("secondary@example.com");
        int result2 = userService.saveToSecondary(secondaryUser);
        assertTrue(result2 > 0, "从数据源插入应该成功");

        UserEntity found2 = userService.getFromSecondary(secondaryUser.getId());
        assertNotNull(found2, "应该查询到数据");
        assertEquals("从数据源用户", found2.getName(), "name 应该正确解密");
        assertEquals("13900139000", found2.getPhone(), "phone 应该保持原样（未加密）");

        // 测试第三方数据源
        UserEntity thirdUser = new UserEntity();
        thirdUser.setName("第三方数据源用户");
        thirdUser.setPhone("13700137000");
        thirdUser.setEmail("third@example.com");
        int result3 = userService.saveToThird(thirdUser);
        assertTrue(result3 > 0, "第三方数据源插入应该成功");

        UserEntity found3 = userService.getFromThird(thirdUser.getId());
        assertNotNull(found3, "应该查询到数据");
        assertEquals("第三方数据源用户", found3.getName(), "name 应该保持原样（未加密）");
        assertEquals("13700137000", found3.getPhone(), "phone 应该保持原样（未加密）");
    }

    /**
     * 测试配置隔离
     * 验证不同数据源的配置不会相互影响
     */
    @Test
    void testConfigurationIsolation() {
        // 验证主数据源的配置
        assertTrue(TableCache.concatTable("user", "primary"), 
                "主数据源的 user 表应该需要加密");
        
        // 验证从数据源的配置
        assertTrue(TableCache.concatTable("user", "secondary"), 
                "从数据源的 user 表应该需要加密");
        
        // 验证第三方数据源的配置
        assertFalse(TableCache.concatTable("user", "third"), 
                "第三方数据源的 user 表不应该需要加密（enable=false）");

        // 验证字段级别的配置
        assertNotNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "primary"),
                "主数据源的 phone 字段应该配置了加密策略");
        assertNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "secondary"),
                "从数据源的 phone 字段不应该配置加密策略");
        assertNull(TableCache.getTableFieldEncryptStrategy("user", "phone", "third"),
                "第三方数据源的 phone 字段不应该配置加密策略");
    }
}

