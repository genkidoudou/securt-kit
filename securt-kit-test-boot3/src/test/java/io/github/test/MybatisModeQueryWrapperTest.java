package io.github.test;

import cn.hutool.db.ds.simple.SimpleDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.cache.StrategyCache;
import io.github.genkidoudou.core.config.ConfigInitializer;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.config.TableConfigRegistry;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import io.github.genkidoudou.mybatis.EncryptInterceptor;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.UserEntityMapper;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mode=MYBATIS 下 MyBatis-Plus QueryWrapper / UpdateWrapper 加密列条件集成测试。
 * <p>
 * 不启动 Spring Boot 容器（避免 Boot3 与 core 内嵌 SLF4J 冲突），
 * 使用普通 H2 + EncryptInterceptor 验证 {@code ew.paramNameValuePairs} 加解密。
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MybatisModeQueryWrapperTest {

    private static final String TEST_TABLE_SQL = "\"user\"";
    private static final String ENC_SUFFIX = "(加密)";

    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    void init() throws Exception {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        StrategyCache.clear();

        MyFieldEncryptorStrategy strategy = new MyFieldEncryptorStrategy();
        StrategyCache.registerStrategy(MyFieldEncryptorStrategy.class, strategy);

        FieldEncryptorProperties props = new FieldEncryptorProperties();
        props.setEnable(true);
        props.setMode(FieldEncryptorProperties.Mode.MYBATIS);

        FieldEncryptorProperties.TableConfig userTable = new FieldEncryptorProperties.TableConfig();
        userTable.setTableName("user");

        FieldEncryptorProperties.FieldConfig nameField = new FieldEncryptorProperties.FieldConfig();
        nameField.setFieldName("name");
        nameField.setStrategy(MyFieldEncryptorStrategy.class.getName());

        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");
        phoneField.setStrategy(MyFieldEncryptorStrategy.class.getName());

        userTable.setFields(Arrays.asList(nameField, phoneField));
        props.setTables(Collections.singletonList(userTable));

        TableCache.init(props);
        assertTrue(EncryptModeHolder.isMybatis(), "EncryptModeHolder 应为 MYBATIS");
        assertNotNull(FieldCryptoServiceHolder.get());

        String url = "jdbc:h2:mem:mybatis_mode_qw_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        dataSource = new SimpleDataSource(url, "sa", "", "org.h2.Driver");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(StdOutImpl.class);
        configuration.addInterceptor(new EncryptInterceptor());
        configuration.addMapper(UserEntityMapper.class);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig());

        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    void tearDown() {
        ConfigInitializer.reset();
        TableConfigRegistry.clear();
        StrategyCache.clear();
    }

    @BeforeEach
    void setUpTable() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE_SQL);
            stmt.execute("CREATE TABLE " + TEST_TABLE_SQL + " (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "name VARCHAR(100), " +
                    "phone VARCHAR(100), " +
                    "age INT, " +
                    "email VARCHAR(100)" +
                    ")");
        }
    }

    @Test
    void queryWrapperEqEncryptedPhone() throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserEntityMapper mapper = session.getMapper(UserEntityMapper.class);
            UserEntity user = insertUser(mapper, "张三", "13800138000", 25);

            assertEquals("13800138000" + ENC_SUFFIX, readRawPhone(user.getId()));

            QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("phone", "13800138000");
            List<UserEntity> list = mapper.selectList(wrapper);

            assertEquals(1, list.size());
            assertEquals("张三", list.get(0).getName());
            assertEquals("13800138000", list.get(0).getPhone());
        }
    }

    @Test
    void lambdaQueryWrapperEqEncryptedPhone() throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserEntityMapper mapper = session.getMapper(UserEntityMapper.class);
            insertUser(mapper, "李四", "13900139000", 30);

            LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserEntity::getPhone, "13900139000");
            List<UserEntity> list = mapper.selectList(wrapper);

            assertEquals(1, list.size());
            assertEquals("李四", list.get(0).getName());
            assertEquals("13900139000", list.get(0).getPhone());
        }
    }

    @Test
    void queryWrapperInEncryptedPhone() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserEntityMapper mapper = session.getMapper(UserEntityMapper.class);
            insertUser(mapper, "王五", "15000150000", 28);
            insertUser(mapper, "赵六", "15100151000", 32);
            insertUser(mapper, "钱七", "15200152000", 33);

            QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
            wrapper.in("phone", Arrays.asList("15000150000", "15100151000"));
            List<UserEntity> list = mapper.selectList(wrapper);

            assertEquals(2, list.size());
            assertTrue(list.stream().anyMatch(u -> "王五".equals(u.getName())));
            assertTrue(list.stream().anyMatch(u -> "赵六".equals(u.getName())));
            assertFalse(list.stream().anyMatch(u -> "钱七".equals(u.getName())));
        }
    }

    @Test
    void updateWrapperSetNameByEncryptedPhone() throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserEntityMapper mapper = session.getMapper(UserEntityMapper.class);
            UserEntity user = insertUser(mapper, "周八", "15300153000", 35);

            UpdateWrapper<UserEntity> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("phone", "15300153000").set("name", "周八(改)");
            assertEquals(1, mapper.update(null, updateWrapper));

            assertEquals("周八(改)" + ENC_SUFFIX, readRawName(user.getId()));

            UserEntity found = mapper.selectById(user.getId());
            assertEquals("周八(改)", found.getName());
            assertEquals("15300153000", found.getPhone());
        }
    }

    @Test
    void queryWrapperEqByAgeStillWorks() throws SQLException {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            UserEntityMapper mapper = session.getMapper(UserEntityMapper.class);
            UserEntity user = insertUser(mapper, "吴九", "15400154000", 27);
            assertEquals("15400154000" + ENC_SUFFIX, readRawPhone(user.getId()));

            QueryWrapper<UserEntity> byAge = new QueryWrapper<>();
            byAge.eq("age", 27);
            assertEquals(1, mapper.selectList(byAge).size());
        }
    }

    private UserEntity insertUser(UserEntityMapper mapper, String name, String phone, int age) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setPhone(phone);
        user.setAge(age);
        user.setEmail(name + "@example.com");
        assertEquals(1, mapper.insert(user));
        assertNotNull(user.getId());
        return user;
    }

    private String readRawPhone(Long id) throws SQLException {
        return readRawColumn(id, "phone");
    }

    private String readRawName(Long id) throws SQLException {
        return readRawColumn(id, "name");
    }

    private String readRawColumn(Long id, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM " + TEST_TABLE_SQL + " WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应能查到记录 id=" + id);
                return rs.getString(1);
            }
        }
    }
}
