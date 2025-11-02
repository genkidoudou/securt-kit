package io.github.test;

import io.github.test.entity.UserEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface UserMapper {
    @Update("UPDATE user SET name = #{name}, phone = #{phone}, age = #{age}, email = #{email} WHERE id = #{id}")
    int updateUser(@Param("name") String name, @Param("phone") String phone,
                   @Param("age") int age, @Param("email") String email, @Param("id") Long id);


    @Update("UPDATE user SET name = #{name},  age = #{age}, email = #{email} WHERE phone = #{phone}")
    int updateUserByPhone(@Param("name") String name, @Param("phone") String phone,
                          @Param("age") int age, @Param("email") String email);


    @Update("INSERT INTO user (id,name, phone, age, email) VALUES (#{id},#{name}, #{phone}, #{age}, #{email})")
    int insertUser(@Param("name") String name, @Param("phone") String phone,
                   @Param("age") int age, @Param("email") String email, @Param("id") Long id);


    @Delete("DELETE FROM user WHERE phone = #{phone}")
    int deleteUserByPhone(@Param("phone") String phone);


    @Select("SELECT  *  FROM user")
    List<Map> selectAll();

    @Select("SELECT id, name, phone, age, email FROM user WHERE phone = #{phone}")
    List<Map> selectByPhone(@Param("phone") String phone);

    @Select("SELECT id, name, phone FROM user WHERE id = #{id}")
    Map selectBasicById(@Param("id") Long id);

    @Select("SELECT name FROM user")
    List<String> selectNames();

    @Select("SELECT COUNT(1) FROM user WHERE age > #{age}")
    int countAgeGreaterThan(@Param("age") int age);

    @Select("SELECT name, phone FROM user WHERE name LIKE #{nameLike}")
    List<Map> selectNamePhoneByLike(@Param("nameLike") String nameLike);

    // === Entity based ===
    @Insert("INSERT INTO user (id,name, phone, age, email) VALUES (#{u.id},#{u.name}, #{u.phone}, #{u.age}, #{u.email})")
    int insertByEntity(@Param("u") UserEntity u);

    @Update("UPDATE user SET name = #{u.name}, phone = #{u.phone}, age = #{u.age}, email = #{u.email} WHERE id = #{u.id}")
    int updateByEntityId(@Param("u") UserEntity u);

    @Select("SELECT id, name, phone, age, email FROM user WHERE id = #{id}")
    @Results(id = "userEntityMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "phone", column = "phone"),
            @Result(property = "age", column = "age"),
            @Result(property = "email", column = "email")
    })
    UserEntity selectEntityById(@Param("id") Long id);

    @Select("SELECT id, name, phone, age, email FROM user")
    @ResultMap("userEntityMap")
    List<UserEntity> selectAllEntities();

    // === Multi-table JOIN queries ===
    @Select("SELECT u.id AS uid, u.name AS uname, u.phone AS uphone, o.id AS oid, o.amount AS oamount FROM user u JOIN orders o ON u.id = o.user_id WHERE o.amount > #{min}")
    List<Map> selectUsersWithOrders(@Param("min") int min);

    @Select("SELECT u.name AS uname, o.amount AS oamount FROM user u LEFT JOIN orders o ON u.id = o.user_id WHERE u.name LIKE #{nameLike}")
    List<Map> selectUserLeftJoinOrdersByName(@Param("nameLike") String nameLike);

    // === INSERT with table alias (using INSERT ... SELECT pattern) ===
    // MySQL 的 INSERT INTO table AS alias 语法不支持，但 INSERT ... SELECT ... FROM table AS alias 支持
    @Insert("INSERT INTO user (id, name, phone, age, email) SELECT #{id}, #{name}, #{phone}, #{age}, #{email} FROM (SELECT 1) AS tmp")
    int insertUserWithAlias(@Param("name") String name, @Param("phone") String phone,
                           @Param("age") int age, @Param("email") String email, @Param("id") Long id);

    // INSERT INTO ... SELECT ... FROM table alias (without AS keyword)
    @Insert("INSERT INTO user (id, name, phone, age, email) SELECT #{id}, #{name}, #{phone}, #{age}, #{email} FROM (SELECT 1) tmp")
    int insertUserWithAliasNoAs(@Param("name") String name, @Param("phone") String phone,
                               @Param("age") int age, @Param("email") String email, @Param("id") Long id);

    // === UPDATE with table alias ===
    @Update("UPDATE user AS u SET u.name = #{name}, u.phone = #{phone}, u.age = #{age}, u.email = #{email} WHERE u.id = #{id}")
    int updateUserWithAlias(@Param("name") String name, @Param("phone") String phone,
                           @Param("age") int age, @Param("email") String email, @Param("id") Long id);

    @Update("UPDATE user u SET u.name = #{name}, u.phone = #{phone}, u.age = #{age}, u.email = #{email} WHERE u.id = #{id}")
    int updateUserWithAliasNoAs(@Param("name") String name, @Param("phone") String phone,
                               @Param("age") int age, @Param("email") String email, @Param("id") Long id);
}
