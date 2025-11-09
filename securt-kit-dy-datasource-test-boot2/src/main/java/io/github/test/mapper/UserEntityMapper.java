package io.github.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.test.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus UserEntity Mapper 接口
 * 
 * <p>MyBatis-Plus BaseMapper 提供了基础的 CRUD 方法：</p>
 * <ul>
 *   <li>insert(T entity) - 插入</li>
 *   <li>deleteById(Serializable id) - 根据ID删除</li>
 *   <li>updateById(T entity) - 根据ID更新</li>
 *   <li>selectById(Serializable id) - 根据ID查询</li>
 *   <li>selectList(Wrapper&lt;T&gt; queryWrapper) - 查询列表</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Mapper
public interface UserEntityMapper extends BaseMapper<UserEntity> {
}

