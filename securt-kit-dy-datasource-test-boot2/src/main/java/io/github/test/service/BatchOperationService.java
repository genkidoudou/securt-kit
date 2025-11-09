package io.github.test.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作服务类
 * 
 * <p>测试批量操作场景下的多数据源加密功能：</p>
 * <ul>
 *   <li>批量插入</li>
 *   <li>批量更新</li>
 *   <li>批量删除</li>
 *   <li>批量查询</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Service
public class BatchOperationService {

    @Autowired
    private UserEntityMapper userEntityMapper;

    /**
     * 测试1：批量插入（主数据源）
     * 测试点：每个用户的 name 和 phone 都应该被加密
     */
    @DS("primary")
    public int batchInsertToPrimary(List<UserEntity> users) {
        int count = 0;
        for (UserEntity user : users) {
            count += userEntityMapper.insert(user);
        }
        return count;
    }

    /**
     * 测试2：批量插入（从数据源）
     * 测试点：每个用户的 name 应该被加密，phone 不应该被加密
     */
    @DS("secondary")
    public int batchInsertToSecondary(List<UserEntity> users) {
        int count = 0;
        for (UserEntity user : users) {
            count += userEntityMapper.insert(user);
        }
        return count;
    }

    /**
     * 测试3：批量更新（主数据源）
     * 测试点：更新 name 和 phone 时应该加密
     */
    @DS("primary")
    public int batchUpdateToPrimary(List<UserEntity> users) {
        int count = 0;
        for (UserEntity user : users) {
            count += userEntityMapper.updateById(user);
        }
        return count;
    }

    /**
     * 测试4：批量更新（从数据源）
     * 测试点：更新 name 时应该加密，phone 不应该被加密
     */
    @DS("secondary")
    public int batchUpdateToSecondary(List<UserEntity> users) {
        int count = 0;
        for (UserEntity user : users) {
            count += userEntityMapper.updateById(user);
        }
        return count;
    }

    /**
     * 测试5：批量查询（主数据源）
     * 测试点：查询结果中的 name 和 phone 应该被自动解密
     */
    @DS("primary")
    public List<UserEntity> batchSelectFromPrimary(List<Long> ids) {
        return userEntityMapper.selectBatchIds(ids);
    }

    /**
     * 测试6：批量查询（从数据源）
     * 测试点：查询结果中的 name 应该被自动解密，phone 保持原样
     */
    @DS("secondary")
    public List<UserEntity> batchSelectFromSecondary(List<Long> ids) {
        return userEntityMapper.selectBatchIds(ids);
    }

    /**
     * 测试7：批量删除（主数据源）
     */
    @DS("primary")
    public int batchDeleteFromPrimary(List<Long> ids) {
        return userEntityMapper.deleteBatchIds(ids);
    }

    /**
     * 测试8：批量删除（从数据源）
     */
    @DS("secondary")
    public int batchDeleteFromSecondary(List<Long> ids) {
        return userEntityMapper.deleteBatchIds(ids);
    }

    /**
     * 测试9：创建测试数据（主数据源）
     */
    @DS("primary")
    public List<UserEntity> createTestDataInPrimary(int count) {
        List<UserEntity> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UserEntity user = new UserEntity();
            user.setName("测试用户" + i);
            user.setPhone("1380000" + String.format("%04d", i));
            user.setAge(20 + i);
            user.setEmail("test" + i + "@example.com");
            userEntityMapper.insert(user);
            users.add(user);
        }
        return users;
    }

    /**
     * 测试10：创建测试数据（从数据源）
     */
    @DS("secondary")
    public List<UserEntity> createTestDataInSecondary(int count) {
        List<UserEntity> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UserEntity user = new UserEntity();
            user.setName("测试用户" + i);
            user.setPhone("1390000" + String.format("%04d", i));
            user.setAge(20 + i);
            user.setEmail("test" + i + "@example.com");
            userEntityMapper.insert(user);
            users.add(user);
        }
        return users;
    }
}

