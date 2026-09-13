package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus UserEntity Mapper 接口
 * 
 * @author hexlodev
 * @since 1.0.0
 */
@Mapper
public interface UserEntityMapper extends BaseMapper<UserEntity> {
    // MyBatis-Plus BaseMapper 提供了基础的 CRUD 方法：
    // - insert(T entity)
    // - deleteById(Serializable id)
    // - updateById(T entity)
    // - selectById(Serializable id)
    // - selectList(Wrapper<T> queryWrapper)
    // 等等...
}

