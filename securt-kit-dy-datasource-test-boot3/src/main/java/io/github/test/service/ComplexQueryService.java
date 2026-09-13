package io.github.test.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.test.entity.OrderEntity;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.OrderEntityMapper;
import io.github.test.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 复杂查询服务类
 * 
 * <p>测试复杂SQL场景下的多数据源加密/解密功能：</p>
 * <ul>
 *   <li>JOIN 查询</li>
 *   <li>子查询</li>
 *   <li>聚合函数</li>
 *   <li>WHERE 条件中的加密字段</li>
 *   <li>GROUP BY / ORDER BY</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Service
public class ComplexQueryService {

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private OrderEntityMapper orderEntityMapper;

    /**
     * 测试1：JOIN 查询（主数据源）
     * SQL: SELECT ... FROM order o INNER JOIN user u ON o.user_id = u.id WHERE u.name = ?
     * 测试点：WHERE 条件中的加密字段查询
     */
    @DS("primary")
    public List<OrderEntity> findOrdersByUserNameInPrimary(String userName) {
        // 先根据用户名查询用户ID（WHERE 条件中包含加密字段）
        UserEntity user = userEntityMapper.selectOne(
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getName, userName)
        );
        if (user == null) {
            return Collections.emptyList();
        }
        // 然后查询该用户的订单
        return orderEntityMapper.selectOrdersByUserId(user.getId());
    }

    /**
     * 测试2：聚合查询（主数据源）
     * SQL: SELECT COUNT(*) FROM order WHERE user_id = ?
     */
    @DS("primary")
    public Long countOrdersByUserIdInPrimary(Long userId) {
        return orderEntityMapper.countOrdersByUserId(userId);
    }

    /**
     * 测试3：聚合查询 - 金额总和（主数据源）
     * SQL: SELECT SUM(amount) FROM order WHERE user_id = ?
     */
    @DS("primary")
    public Double sumOrderAmountByUserIdInPrimary(Long userId) {
        return orderEntityMapper.sumAmountByUserId(userId);
    }

    /**
     * 测试4：JOIN 查询（从数据源）
     */
    @DS("secondary")
    public List<OrderEntity> findOrdersByUserNameInSecondary(String userName) {
        UserEntity user = userEntityMapper.selectOne(
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getName, userName)
        );
        if (user == null) {
            return Collections.emptyList();
        }
        return orderEntityMapper.selectOrdersByUserId(user.getId());
    }

    /**
     * 测试5：复杂 WHERE 条件（主数据源）
     * SQL: SELECT ... FROM user WHERE name = ? AND phone = ?
     */
    @DS("primary")
    public List<UserEntity> findUsersByNameAndPhoneInPrimary(String name, String phone) {
        return userEntityMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getName, name)
                        .eq(UserEntity::getPhone, phone)
        );
    }

    /**
     * 测试6：复杂 WHERE 条件（从数据源）
     * SQL: SELECT ... FROM user WHERE name = ? AND phone = ?
     * 注意：从数据源只加密 name，不加密 phone
     */
    @DS("secondary")
    public List<UserEntity> findUsersByNameAndPhoneInSecondary(String name, String phone) {
        return userEntityMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getName, name)
                        .eq(UserEntity::getPhone, phone)
        );
    }

    /**
     * 测试7：LIKE 查询（主数据源）
     * SQL: SELECT ... FROM user WHERE name LIKE ?
     */
    @DS("primary")
    public List<UserEntity> findUsersByNameLikeInPrimary(String namePattern) {
        return userEntityMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .like(UserEntity::getName, namePattern)
        );
    }

    /**
     * 测试8：IN 查询（主数据源）
     * SQL: SELECT ... FROM user WHERE name IN (?, ?, ?)
     */
    @DS("primary")
    public List<UserEntity> findUsersByNameInInPrimary(List<String> names) {
        return userEntityMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .in(UserEntity::getName, names)
        );
    }

    /**
     * 测试9：范围查询（主数据源）
     * SQL: SELECT ... FROM user WHERE age BETWEEN ? AND ?
     */
    @DS("primary")
    public List<UserEntity> findUsersByAgeRangeInPrimary(Integer minAge, Integer maxAge) {
        return userEntityMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .between(UserEntity::getAge, minAge, maxAge)
        );
    }
}

