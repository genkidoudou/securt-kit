package io.github.test;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
