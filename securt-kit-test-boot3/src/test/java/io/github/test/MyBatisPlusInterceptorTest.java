package io.github.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import com.example.entity.UserEntity;
import com.example.mapper.UserEntityMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 与拦截器集成测试类
 * 
 * 测试 MyBatis-Plus 的基础 CRUD、条件查询、分页等功能，
 * 同时验证拦截器对 SQL 的拦截和加密/解密功能
 * 
 * @author hexlodev
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MyBatisPlusInterceptorTest {

    private static final String TEST_TABLE = "user";
    private static final String DB_URL = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";

    @Autowired(required = false)
    private UserEntityMapper userEntityMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void setUp() {
        // 加载拦截器驱动
        try {
            Class.forName("io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SimpleInterceptorDriver", e);
        }

        // 初始化加密配置
        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName(TEST_TABLE);

        // 配置name和phone字段需要加密
        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");

        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");

        FieldEncryptorProperties.FieldConfig idField = new FieldEncryptorProperties.FieldConfig();
        idField.setFieldName("id");

        userTable.setFields(java.util.Arrays.asList(nameField, phoneField, idField));
        props.setTables(java.util.Collections.singletonList(userTable));

        TableCache.init(props);
    }

    /**
     * 创建测试表
     */
    @BeforeEach
    void setUpTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
                stmt.execute("CREATE TABLE " + TEST_TABLE + " (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                        "name VARCHAR(100), " +
                        "phone VARCHAR(100), " +
                        "age INT, " +
                        "email VARCHAR(100)" +
                        ")");
            }
        }
    }

    /**
     * 测试 insert - 使用 MyBatis-Plus 的 insert 方法
     */
    @Test
    void testInsert() {
        UserEntity user = new UserEntity();
        user.setName("张三");
        user.setPhone("13800138000");
        user.setAge(25);
        user.setEmail("zhangsan@example.com");

        int result = userEntityMapper.insert(user);
        System.out.println("testInsert -> affected rows: " + result + ", inserted id: " + user.getId());
        assertEquals(1, result, "应该插入一条记录");
        assertNotNull(user.getId(), "ID 应该自动生成");
    }

    /**
     * 测试 selectById - 根据ID查询
     */
    @Test
    void testSelectById() {
        // 先插入一条数据
        UserEntity user = new UserEntity();
        user.setName("李四");
        user.setPhone("13900139000");
        user.setAge(30);
        user.setEmail("lisi@example.com");
        userEntityMapper.insert(user);
        Long id = user.getId();

        // 查询
        UserEntity found = userEntityMapper.selectById(id);
        System.out.println("testSelectById -> found: id=" + found.getId() + 
                ", name=" + found.getName() + ", phone=" + found.getPhone());
        assertNotNull(found, "应该能找到数据");
        assertEquals("李四", found.getName(), "姓名应该被正确解密");
        assertEquals("13900139000", found.getPhone(), "手机号应该被正确解密");
    }

    /**
     * 测试 selectList - 查询列表
     */
    @Test
    void testSelectList() {
        // 插入多条数据
        UserEntity user1 = new UserEntity();
        user1.setName("王五");
        user1.setPhone("15000150000");
        user1.setAge(28);
        user1.setEmail("wangwu@example.com");
        userEntityMapper.insert(user1);

        UserEntity user2 = new UserEntity();
        user2.setName("赵六");
        user2.setPhone("15100151000");
        user2.setAge(32);
        user2.setEmail("zhaoliu@example.com");
        userEntityMapper.insert(user2);

        // 查询所有
        List<UserEntity> list = userEntityMapper.selectList(null);
        System.out.println("testSelectList -> list size: " + list.size());
        assertTrue(list.size() >= 2, "应该至少有2条记录");
        
        // 验证解密
        UserEntity found = list.stream()
                .filter(u -> "王五".equals(u.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(found, "应该能找到王五");
        assertEquals("15000150000", found.getPhone(), "手机号应该被正确解密");
    }

    /**
     * 测试 selectList with QueryWrapper - 条件查询
     */
    @Test
    void testSelectListWithQueryWrapper() {
        // 插入数据
        UserEntity user1 = new UserEntity();
        user1.setName("孙七");
        user1.setPhone("15200152000");
        user1.setAge(25);
        userEntityMapper.insert(user1);

        UserEntity user2 = new UserEntity();
        user2.setName("周八");
        user2.setPhone("15300153000");
        user2.setAge(35);
        userEntityMapper.insert(user2);

        // 使用 QueryWrapper 查询年龄大于30的
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.gt("age", 30);
        List<UserEntity> list = userEntityMapper.selectList(queryWrapper);
        
        System.out.println("testSelectListWithQueryWrapper -> list size: " + list.size());
        assertEquals(1, list.size(), "应该只有1条记录");
        assertEquals("周八", list.get(0).getName(), "应该是周八");
    }

    /**
     * 测试 selectList with LambdaQueryWrapper - Lambda 表达式条件查询
     */
    @Test
    void testSelectListWithLambdaQueryWrapper() {
        // 插入数据
        UserEntity user = new UserEntity();
        user.setName("吴九");
        user.setPhone("15400154000");
        user.setAge(27);
        user.setEmail("wujiu@example.com");
        userEntityMapper.insert(user);

        // 使用 LambdaQueryWrapper
        LambdaQueryWrapper<UserEntity> lambdaQuery = new LambdaQueryWrapper<>();
        lambdaQuery.eq(UserEntity::getAge, 27)
                   .like(UserEntity::getName, "九");
        
        List<UserEntity> list = userEntityMapper.selectList(lambdaQuery);
        System.out.println("testSelectListWithLambdaQueryWrapper -> list size: " + list.size());
        assertEquals(1, list.size(), "应该只有1条记录");
        assertEquals("吴九", list.get(0).getName(), "姓名应该被正确解密");
    }

    /**
     * 测试 updateById - 根据ID更新
     */
    @Test
    void testUpdateById() {
        // 先插入
        UserEntity user = new UserEntity();
        user.setName("郑十");
        user.setPhone("15500155000");
        user.setAge(29);
        userEntityMapper.insert(user);
        Long id = user.getId();

        // 更新
        user.setName("郑十(更新)");
        user.setPhone("15500155001");
        user.setAge(30);
        int result = userEntityMapper.updateById(user);
        
        System.out.println("testUpdateById -> affected rows: " + result);
        assertEquals(1, result, "应该更新1条记录");

        // 验证更新
        UserEntity updated = userEntityMapper.selectById(id);
        assertEquals("郑十(更新)", updated.getName(), "姓名应该被更新并正确解密");
        assertEquals("15500155001", updated.getPhone(), "手机号应该被更新并正确解密");
    }

    /**
     * 测试 update with UpdateWrapper - 使用条件更新
     */
    @Test
    void testUpdateWithUpdateWrapper() {
        // 插入数据
        UserEntity user = new UserEntity();
        user.setName("钱一");
        user.setPhone("15600156000");
        user.setAge(26);
        userEntityMapper.insert(user);

        // 使用 UpdateWrapper 更新
        UpdateWrapper<UserEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("age", 26)
                     .set("age", 27)
                     .set("email", "qianyi@example.com");
        
        int result = userEntityMapper.update(null, updateWrapper);
        System.out.println("testUpdateWithUpdateWrapper -> affected rows: " + result);
        assertEquals(1, result, "应该更新1条记录");
    }

    /**
     * 测试 update with LambdaUpdateWrapper - 使用 Lambda 表达式更新
     */
    @Test
    void testUpdateWithLambdaUpdateWrapper() {
        // 插入数据
        UserEntity user = new UserEntity();
        user.setName("钱二");
        user.setPhone("15700157000");
        user.setAge(28);
        userEntityMapper.insert(user);
        Long id = user.getId();

        // 使用 LambdaUpdateWrapper
        LambdaUpdateWrapper<UserEntity> lambdaUpdate = new LambdaUpdateWrapper<>();
        lambdaUpdate.eq(UserEntity::getId, id)
                    .set(UserEntity::getName, "钱二(更新)")
                    .set(UserEntity::getAge, 29);
        
        int result = userEntityMapper.update(null, lambdaUpdate);
        System.out.println("testUpdateWithLambdaUpdateWrapper -> affected rows: " + result);
        assertEquals(1, result, "应该更新1条记录");

        // 验证更新
        UserEntity updated = userEntityMapper.selectById(id);
        assertEquals("钱二(更新)", updated.getName(), "姓名应该被更新并正确解密");
    }

    /**
     * 测试 deleteById - 根据ID删除
     */
    @Test
    void testDeleteById() {
        // 插入数据
        UserEntity user = new UserEntity();
        user.setName("孙三");
        user.setPhone("15800158000");
        user.setAge(31);
        userEntityMapper.insert(user);
        Long id = user.getId();

        // 删除
        int result = userEntityMapper.deleteById(id);
        System.out.println("testDeleteById -> affected rows: " + result);
        assertEquals(1, result, "应该删除1条记录");

        // 验证删除
        UserEntity deleted = userEntityMapper.selectById(id);
        assertNull(deleted, "应该找不到已删除的数据");
    }

    /**
     * 测试 delete with QueryWrapper - 条件删除
     */
    @Test
    void testDeleteWithQueryWrapper() {
        // 插入数据
        UserEntity user1 = new UserEntity();
        user1.setName("李四");
        user1.setPhone("15900159000");
        user1.setAge(25);
        userEntityMapper.insert(user1);

        UserEntity user2 = new UserEntity();
        user2.setName("李四");
        user2.setPhone("16000160000");
        user2.setAge(25);
        userEntityMapper.insert(user2);

        // 删除年龄为25的所有记录
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("age", 25);
        int result = userEntityMapper.delete(queryWrapper);
        
        System.out.println("testDeleteWithQueryWrapper -> affected rows: " + result);
        assertEquals(2, result, "应该删除2条记录");
    }

    /**
     * 测试 selectPage - 分页查询
     */
    @Test
    void testSelectPage() {
        // 插入多条数据
        for (int i = 1; i <= 5; i++) {
            UserEntity user = new UserEntity();
            user.setName("用户" + i);
            user.setPhone("1700017000" + i);
            user.setAge(20 + i);
            userEntityMapper.insert(user);
        }

        // 分页查询
        Page<UserEntity> page = new Page<>(1, 2); // 第1页，每页2条
        IPage<UserEntity> pageResult = userEntityMapper.selectPage(page, null);
        
        System.out.println("testSelectPage -> total: " + pageResult.getTotal() + 
                ", current: " + pageResult.getCurrent() + 
                ", size: " + pageResult.getSize() + 
                ", records size: " + pageResult.getRecords().size());
        
        assertEquals(5, pageResult.getTotal(), "总记录数应该是5");
        assertEquals(2, pageResult.getRecords().size(), "当前页应该有2条记录");
    }

    /**
     * 测试 selectPage with QueryWrapper - 分页条件查询
     */
    @Test
    void testSelectPageWithQueryWrapper() {
        // 插入数据
        for (int i = 1; i <= 10; i++) {
            UserEntity user = new UserEntity();
            user.setName("分页用户" + i);
            user.setPhone("1800018000" + (i < 10 ? "0" + i : i));
            user.setAge(20 + i);
            userEntityMapper.insert(user);
        }

        // 分页查询年龄大于25的
        Page<UserEntity> page = new Page<>(1, 3);
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.gt("age", 25);
        
        IPage<UserEntity> pageResult = userEntityMapper.selectPage(page, queryWrapper);
        
        System.out.println("testSelectPageWithQueryWrapper -> total: " + pageResult.getTotal() + 
                ", records size: " + pageResult.getRecords().size());
        
        assertTrue(pageResult.getTotal() > 0, "应该有查询结果");
        assertTrue(pageResult.getRecords().size() <= 3, "当前页记录数不应该超过3");
        
        // 验证所有记录的年龄都大于25
        pageResult.getRecords().forEach(u -> {
            assertTrue(u.getAge() > 25, "年龄应该大于25");
            System.out.println("  -> name: " + u.getName() + ", age: " + u.getAge());
        });
    }

    /**
     * 测试 selectCount - 统计查询
     */
    @Test
    void testSelectCount() {
        // 插入数据
        UserEntity user1 = new UserEntity();
        user1.setName("统计用户1");
        user1.setAge(30);
        userEntityMapper.insert(user1);

        UserEntity user2 = new UserEntity();
        user2.setName("统计用户2");
        user2.setAge(30);
        userEntityMapper.insert(user2);

        UserEntity user3 = new UserEntity();
        user3.setName("统计用户3");
        user3.setAge(31);
        userEntityMapper.insert(user3);

        // 统计年龄为30的记录数
        QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("age", 30);
        Long count = userEntityMapper.selectCount(queryWrapper);
        
        System.out.println("testSelectCount -> count: " + count);
        assertEquals(2, count, "应该有2条年龄为30的记录");
    }
}

