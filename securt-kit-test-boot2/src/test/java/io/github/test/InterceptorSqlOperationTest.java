package io.github.test;

import cn.hutool.db.ds.simple.SimpleDataSource;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import com.example.entity.UserEntity;

/**
 * 测试拦截器对SQL操作的拦截和处理（增删改查）
 *
 * @author hexlodev
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InterceptorSqlOperationTest {

    private static final String TEST_TABLE = "user";
    private static final String DB_URL = "jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";

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
        // 如果没有配置strategy，会使用默认的FieldEncryptorStrategy
        FieldEncryptorProperties.FieldConfig phoneField = new FieldEncryptorProperties.FieldConfig();
        phoneField.setFieldName("phone");

        // 如果没有配置strategy，会使用默认的FieldEncryptorStrategy
        FieldEncryptorProperties.FieldConfig idField = new FieldEncryptorProperties.FieldConfig();
        idField.setFieldName("id");

        userTable.setFields(java.util.Arrays.asList(nameField, phoneField, idField));
        props.setTables(java.util.Collections.singletonList(userTable));

        TableCache.init(props);
    }

    /**
     * 创建测试表
     */
    private void setUpTable(Connection conn) throws SQLException {
        // 创建测试表
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
            stmt.execute("CREATE TABLE " + TEST_TABLE + " (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "name VARCHAR(100), " +
                    "phone VARCHAR(100), " +  // 增加到100以容纳加密后的值（MD5为32字符）
                    "age INT, " +
                    "email VARCHAR(100)" +
                    ")");
        }
    }

    /**
     * 创建订单表（多表测试用）
     */
    private void setUpOrdersTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("CREATE TABLE orders (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "user_id BIGINT, " +
                    "amount INT)");
        }
    }


    @Test
    void testUpdate() throws Exception {
        try (Connection conn = getDataSource().getConnection()) {
            setUpTable(conn);
            // 先插入一条数据
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO "user" (id,name,phone,age,email) VALUES (1,'','',0,'')");
            }
        }
        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.updateUser("张三", "19112341234", 14, "张三@163.com", 1L);
            System.out.println("testUpdate -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被更新");
        }
    }


    @Test
    void insertUser() throws SQLException, IOException {
        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.insertUser("张三", "19112341234", 14, "张三@163.com", 1L);
            System.out.println("insertUser -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被更新");
        }
    }

    @Test
    void testUpdate2() throws Exception {

        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.insertUser("张三", "19112341234", 14, "张三@163.com", 1L);
            int rows2 = mapper.updateUserByPhone("张三", "19112341234", 14, "张三@163.com");
            System.out.println("testUpdate2 -> affected rows: " + rows2);
            assertEquals(1, rows2, "应该有一行被更新");
        }
    }


    @Test
    void testDelete() throws Exception {

        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.insertUser("张三", "19112341234", 14, "张三@163.com", 1L);
            int rows2 = mapper.deleteUserByPhone("19112341234");
            System.out.println("testDelete -> affected rows: " + rows2);
            assertEquals(1, rows2, "应该有一行被更新");
        }
    }

    @Test

    public void select1() throws IOException, SQLException {
        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.insertUser("张三", "19112341234", 14, "张三@163.com", 1L);
            List<Map> maps = mapper.selectAll();
            for (Map map : maps) {
                System.out.println(map);
            }
        }
    }

    @Test
    void selectAll_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
            List<Map> all = mapper.selectAll();
            System.out.println("selectAll_shouldDecrypt -> result: " + all);
            assertEquals(2, all.size());
        }
    }

    @Test
    void selectByPhone_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 1L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            List<Map> byPhone = mapper.selectByPhone("19112341234");
            System.out.println("selectByPhone_shouldDecrypt -> result: " + byPhone);
            assertEquals(1, byPhone.size());
            Map row = byPhone.get(0);
            System.out.println("selectByPhone_shouldDecrypt -> first row: " + row);
            assertEquals("张三", row.get("NAME") != null ? row.get("NAME") : row.get("name"));
            assertEquals("19112341234", row.get("PHONE") != null ? row.get("PHONE") : row.get("phone"));
        }
    }

    @Test
    void selectBasicById_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
            Map one = mapper.selectBasicById(2L);
            System.out.println("selectBasicById_shouldDecrypt -> result: " + one);
            assertEquals("李四", one.get("NAME") != null ? one.get("NAME") : one.get("name"));
        }
    }

    @Test
    void selectNames_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
            List<String> names = mapper.selectNames();
            System.out.println("selectNames_shouldDecrypt -> result: " + names);
            assertTrue(names.contains("张三"));
            assertTrue(names.contains("李四"));
        }
    }

    @Test
    void countAgeGreaterThan_shouldWork() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
            int cnt = mapper.countAgeGreaterThan(10);
            System.out.println("countAgeGreaterThan_shouldWork -> count: " + cnt);
            assertEquals(2, cnt);
        }
    }

    @Test
    void selectNamePhoneByLike_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
            List<Map> likeRows = mapper.selectNamePhoneByLike("张%");
            System.out.println("selectNamePhoneByLike_shouldDecrypt -> result: " + likeRows);
            assertEquals(1, likeRows.size());
            Map lr = likeRows.get(0);
            assertEquals("张三", lr.get("NAME") != null ? lr.get("NAME") : lr.get("name"));
            assertEquals("19112341234", lr.get("PHONE") != null ? lr.get("PHONE") : lr.get("phone"));
        }
    }

    @Test
    void insertByEntity_shouldSucceed() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            UserEntity u1 = new UserEntity();
            u1.setId(1L);
            u1.setName("");
            u1.setPhone("");
            u1.setAge(0);
            u1.setEmail("");
            int rows = mapper.insertByEntity(u1);
            System.out.println("insertByEntity_shouldSucceed -> affected rows: " + rows);
            assertEquals(1, rows);
        }
    }

    @Test
    void updateByEntityId_shouldEncrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            UserEntity u1 = new UserEntity();
            u1.setId(1L);
            u1.setName("");
            u1.setPhone("");
            u1.setAge(0);
            u1.setEmail("");
            mapper.insertByEntity(u1);

            u1.setName("王五");
            u1.setPhone("18800000000");
            u1.setAge(30);
            u1.setEmail("w@w.com");
            int updated = mapper.updateByEntityId(u1);
            System.out.println("updateByEntityId_shouldEncrypt -> affected rows: " + updated);
            assertEquals(1, updated);
        }
    }

    @Test
    void selectEntityById_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            UserEntity u1 = new UserEntity();
            u1.setId(1L);
            u1.setName("");
            u1.setPhone("");
            u1.setAge(0);
            u1.setEmail("");
            mapper.insertByEntity(u1);
            u1.setName("王五");
            u1.setPhone("18800000000");
            u1.setAge(30);
            u1.setEmail("w@w.com");
            mapper.updateByEntityId(u1);
            UserEntity got = mapper.selectEntityById(1L);
            System.out.println("selectEntityById_shouldDecrypt -> result: id=" + got.getId() + ", name=" + got.getName() + ", phone=" + got.getPhone() + ", age=" + got.getAge() + ", email=" + got.getEmail());
            assertEquals("王五", got.getName());
            assertEquals("18800000000", got.getPhone());
            assertEquals(30, got.getAge());
        }
    }

    @Test
    void selectAllEntities_shouldDecrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            UserEntity u1 = new UserEntity();
            u1.setId(1L);
            u1.setName("");
            u1.setPhone("");
            u1.setAge(0);
            u1.setEmail("");
            mapper.insertByEntity(u1);
            u1.setName("王五");
            u1.setPhone("18800000000");
            u1.setAge(30);
            u1.setEmail("w@w.com");
            mapper.updateByEntityId(u1);
            java.util.List<UserEntity> list = mapper.selectAllEntities();
            System.out.println("selectAllEntities_shouldDecrypt -> result size: " + list.size() + ", first: " + (list.isEmpty() ? null : (list.get(0).getId() + "/" + list.get(0).getName())));
            assertEquals(1, list.size());
            assertEquals("王五", list.get(0).getName());
        }
    }

    @Test
    void selectUsersWithOrders_shouldDecryptUserColumns() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            setUpOrdersTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);

            // 初始化 user 表
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);

            // 初始化 orders 表
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("INSERT INTO orders (id,user_id,amount) VALUES (1,1,100)");
                st.executeUpdate("INSERT INTO orders (id,user_id,amount) VALUES (2,2,50)");
            }

            List<Map> rows = mapper.selectUsersWithOrders(60);
            System.out.println("selectUsersWithOrders_shouldDecryptUserColumns -> result: " + rows);
            // 只应命中 user_id=1 的订单
            assertEquals(1, rows.size());
            Map r = rows.get(0);
            // 列别名：uname/uphone
            assertEquals("张三", r.get("UNAME") != null ? r.get("UNAME") : r.get("uname"));
            assertEquals("19112341234", r.get("UPHONE") != null ? r.get("UPHONE") : r.get("uphone"));
            assertEquals(100, ((Number) (r.get("OAMOUNT") != null ? r.get("OAMOUNT") : r.get("oamount"))).intValue());
        }
    }

    @Test
    void selectUserLeftJoinOrdersByName_shouldDecryptUserColumns() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            setUpOrdersTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);

//            // 初始化 user 表
            mapper.insertUser("", "", 0, "", 1L);
            mapper.insertUser("", "", 0, "", 2L);
            mapper.updateUser("张三", "19112341234", 18, "a@a.com", 1L);
            mapper.updateUser("李四", "13900000000", 28, "b@b.com", 2L);
//
            // 初始化 orders 表（只给张三一条订单）
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("INSERT INTO orders (id,user_id,amount) VALUES (1,1,100)");
            }

            List<Map> rows = mapper.selectUserLeftJoinOrdersByName("张%");
            System.out.println("selectUserLeftJoinOrdersByName_shouldDecryptUserColumns -> result: " + rows);
            assertEquals(1, rows.size());
            Map r = rows.get(0);
            assertEquals("张三", r.get("UNAME") != null ? r.get("UNAME") : r.get("uname"));
            assertEquals(100, ((Number) (r.get("OAMOUNT") != null ? r.get("OAMOUNT") : r.get("oamount"))).intValue());
        }
    }

    @Test
    void insertUserWithAlias_shouldEncrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            
            // 使用带 AS 别名的 INSERT 语句
            int rows = mapper.insertUserWithAlias("赵六", "15555555555", 25, "zhao@example.com", 1L);
            System.out.println("insertUserWithAlias_shouldEncrypt -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被插入");
            
            // 验证数据是否正确插入（会触发解密）
            List<Map> result = mapper.selectByPhone("15555555555");
            System.out.println("insertUserWithAlias_shouldEncrypt -> query result: " + result);
            assertTrue(result.size() > 0, "应该能查询到插入的数据");
        }
    }

    @Test
    void insertUserWithAliasNoAs_shouldEncrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            
            // 使用不带 AS 的别名 INSERT 语句
            int rows = mapper.insertUserWithAliasNoAs("钱七", "16666666666", 26, "qian@example.com", 2L);
            System.out.println("insertUserWithAliasNoAs_shouldEncrypt -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被插入");
            
            // 验证数据是否正确插入
            List<Map> result = mapper.selectByPhone("16666666666");
            System.out.println("insertUserWithAliasNoAs_shouldEncrypt -> query result: " + result);
            assertTrue(result.size() > 0, "应该能查询到插入的数据");
        }
    }

    @Test
    void updateUserWithAlias_shouldEncrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            
            // 先插入一条空数据
            mapper.insertUser("", "", 0, "", 1L);
            
            // 使用带 AS 别名的 UPDATE 语句，字段前都加上了别名前缀
            int rows = mapper.updateUserWithAlias("孙八", "17777777777", 27, "sun@example.com", 1L);
            System.out.println("updateUserWithAlias_shouldEncrypt -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被更新");
            
            // 验证数据是否正确更新（会触发解密）
            List<Map> result = mapper.selectByPhone("17777777777");
            System.out.println("updateUserWithAlias_shouldEncrypt -> query result: " + result);
            assertTrue(result.size() > 0, "应该能查询到更新的数据");
            if (!result.isEmpty()) {
                Map r = result.get(0);
                assertEquals("孙八", r.get("NAME") != null ? r.get("NAME") : r.get("name"));
            }
        }
    }

    @Test
    void updateUserWithAliasNoAs_shouldEncrypt() throws Exception {
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            
            // 先插入一条空数据
            mapper.insertUser("", "", 0, "", 2L);
            
            // 使用不带 AS 的别名 UPDATE 语句，字段前都加上了别名前缀
            int rows = mapper.updateUserWithAliasNoAs("周九", "18888888888", 28, "zhou@example.com", 2L);
            System.out.println("updateUserWithAliasNoAs_shouldEncrypt -> affected rows: " + rows);
            assertEquals(1, rows, "应该有一行被更新");
            
            // 验证数据是否正确更新（会触发解密）
            List<Map> result = mapper.selectByPhone("18888888888");
            System.out.println("updateUserWithAliasNoAs_shouldEncrypt -> query result: " + result);
            assertTrue(result.size() > 0, "应该能查询到更新的数据");
            if (!result.isEmpty()) {
                Map r = result.get(0);
                assertEquals("周九", r.get("NAME") != null ? r.get("NAME") : r.get("name"));
            }
        }
    }

    private DataSource getDataSource() throws SQLException {
        return new SimpleDataSource(DB_URL, "sa", "", "io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
    }
}

