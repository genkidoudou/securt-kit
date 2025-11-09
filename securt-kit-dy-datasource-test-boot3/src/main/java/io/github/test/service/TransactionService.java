package io.github.test.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import io.github.test.entity.OrderEntity;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.OrderEntityMapper;
import io.github.test.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 事务服务类
 * 
 * <p>测试事务场景下的多数据源加密功能：</p>
 * <ul>
 *   <li>单数据源事务</li>
 *   <li>跨数据源操作（非事务）</li>
 *   <li>事务回滚场景</li>
 *   <li>嵌套事务</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Service
public class TransactionService {

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private OrderEntityMapper orderEntityMapper;

    /**
     * 测试1：单数据源事务 - 创建用户和订单（主数据源）
     * 测试点：事务中的多个操作都应该正确加密
     */
    @DS("primary")
    @Transactional(rollbackFor = Exception.class)
    public void createUserAndOrderInPrimary(UserEntity user, OrderEntity order) {
        // 插入用户（name 和 phone 应该被加密）
        userEntityMapper.insert(user);
        
        // 设置订单的用户ID
        order.setUserId(user.getId());
        
        // 插入订单
        orderEntityMapper.insert(order);
    }

    /**
     * 测试2：单数据源事务 - 创建用户和订单（从数据源）
     * 测试点：事务中的多个操作都应该正确加密（只加密 name）
     */
    @DS("secondary")
    @Transactional(rollbackFor = Exception.class)
    public void createUserAndOrderInSecondary(UserEntity user, OrderEntity order) {
        // 插入用户（只有 name 应该被加密）
        userEntityMapper.insert(user);
        
        // 设置订单的用户ID
        order.setUserId(user.getId());
        
        // 插入订单
        orderEntityMapper.insert(order);
    }

    /**
     * 测试3：事务回滚场景（主数据源）
     * 测试点：即使事务回滚，加密操作也应该正常执行
     */
    @DS("primary")
    @Transactional(rollbackFor = Exception.class)
    public void createUserWithRollbackInPrimary(UserEntity user, boolean shouldRollback) {
        // 插入用户（name 和 phone 应该被加密）
        userEntityMapper.insert(user);
        
        if (shouldRollback) {
            throw new RuntimeException("模拟事务回滚");
        }
    }

    /**
     * 测试4：跨数据源操作（非事务）
     * 测试点：在不同数据源中操作，每个数据源使用自己的加密配置
     */
    public void createUserInMultipleDatasources(UserEntity user) {
        // 在主数据源中创建用户（加密 name 和 phone）
        saveToPrimary(user);
        
        // 在从数据源中创建用户（只加密 name）
        saveToSecondary(user);
        
        // 在第三方数据源中创建用户（不加密）
        saveToThird(user);
    }

    @DS("primary")
    private void saveToPrimary(UserEntity user) {
        userEntityMapper.insert(user);
    }

    @DS("secondary")
    private void saveToSecondary(UserEntity user) {
        userEntityMapper.insert(user);
    }

    @DS("third")
    private void saveToThird(UserEntity user) {
        userEntityMapper.insert(user);
    }

    /**
     * 测试5：批量事务操作（主数据源）
     * 测试点：事务中的批量操作都应该正确加密
     */
    @DS("primary")
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateUsersInPrimary(List<UserEntity> users) {
        for (UserEntity user : users) {
            userEntityMapper.insert(user);
        }
    }

    /**
     * 测试6：复杂事务操作（主数据源）
     * 测试点：事务中包含查询、插入、更新等多种操作
     */
    @DS("primary")
    @Transactional(rollbackFor = Exception.class)
    public void complexTransactionInPrimary(UserEntity user, OrderEntity order) {
        // 1. 查询用户（解密）
        UserEntity existingUser = userEntityMapper.selectById(user.getId());
        
        if (existingUser != null) {
            // 2. 更新用户（加密）
            existingUser.setName(user.getName());
            existingUser.setPhone(user.getPhone());
            userEntityMapper.updateById(existingUser);
        } else {
            // 3. 插入用户（加密）
            userEntityMapper.insert(user);
        }
        
        // 4. 插入订单
        order.setUserId(user.getId());
        orderEntityMapper.insert(order);
    }
}

