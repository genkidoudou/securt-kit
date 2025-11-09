package io.github.test.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import io.github.test.entity.UserEntity;
import io.github.test.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多数据源用户服务类
 * 
 * <p>演示如何使用 @DS 注解切换数据源</p>
 * 
 * <p>使用说明：</p>
 * <ul>
 *   <li>@DS("primary") - 使用主数据源（加密 name 和 phone）</li>
 *   <li>@DS("secondary") - 使用从数据源（只加密 name）</li>
 *   <li>@DS("third") - 使用第三方数据源（不加密）</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@Service
public class MultiDataSourceUserService {

    @Autowired
    private UserEntityMapper userEntityMapper;

    /**
     * 在主数据源中保存用户（加密 name 和 phone）
     */
    @DS("primary")
    public int saveToPrimary(UserEntity user) {
        return userEntityMapper.insert(user);
    }

    /**
     * 在主数据源中查询用户（自动解密 name 和 phone）
     */
    @DS("primary")
    public UserEntity getFromPrimary(Long id) {
        return userEntityMapper.selectById(id);
    }

    /**
     * 在主数据源中查询所有用户
     */
    @DS("primary")
    public List<UserEntity> listFromPrimary() {
        return userEntityMapper.selectList(null);
    }

    /**
     * 在从数据源中保存用户（只加密 name，不加密 phone）
     */
    @DS("secondary")
    public int saveToSecondary(UserEntity user) {
        return userEntityMapper.insert(user);
    }

    /**
     * 在从数据源中查询用户（自动解密 name，phone 保持原样）
     */
    @DS("secondary")
    public UserEntity getFromSecondary(Long id) {
        return userEntityMapper.selectById(id);
    }

    /**
     * 在从数据源中查询所有用户
     */
    @DS("secondary")
    public List<UserEntity> listFromSecondary() {
        return userEntityMapper.selectList(null);
    }

    /**
     * 在第三方数据源中保存用户（不加密任何字段）
     */
    @DS("third")
    public int saveToThird(UserEntity user) {
        return userEntityMapper.insert(user);
    }

    /**
     * 在第三方数据源中查询用户（所有字段保持原样）
     */
    @DS("third")
    public UserEntity getFromThird(Long id) {
        return userEntityMapper.selectById(id);
    }

    /**
     * 在第三方数据源中查询所有用户
     */
    @DS("third")
    public List<UserEntity> listFromThird() {
        return userEntityMapper.selectList(null);
    }
}

