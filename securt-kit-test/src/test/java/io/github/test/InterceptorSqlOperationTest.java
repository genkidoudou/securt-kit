package io.github.test;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.config.FieldEncryptorProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

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


    @Test
    void testUpdate() throws Exception {
        try (Connection conn = getDataSource().getConnection()) {
            setUpTable(conn);
            // 先插入一条数据
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO user (id,name,phone,age,email) VALUES (1,'','',0,'')");
            }
        }
        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.updateUser("张三", "19112341234", 14, "张三@163.com", 1L);
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
            assertEquals(1, rows2, "应该有一行被更新");
        }
    }



    @Test
    void  testDelete() throws Exception {

        // 加载MyBatis配置
        java.io.InputStream input = Resources.getResourceAsStream("mybatis-config-test.xml");
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(input);
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            Connection connection = sqlSession.getConnection();
            setUpTable(connection);
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int rows = mapper.insertUser("张三", "19112341234", 14, "张三@163.com", 1L);
            int rows2 = mapper.deleteUserByPhone("19112341234");
            assertEquals(1, rows2, "应该有一行被更新");
        }
    }

    private DataSource getDataSource() throws SQLException {
        return new SimpleDataSource(DB_URL, "sa", "", "io.github.hexlodev.core.interceptor.SimpleInterceptorDriver");
    }
}

