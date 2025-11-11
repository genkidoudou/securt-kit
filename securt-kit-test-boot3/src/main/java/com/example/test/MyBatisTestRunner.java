package com.example.test;

import com.example.entity.UserEntity;
import com.example.mapper.UserEntityMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MyBatis 测试类
 * 不使用 @Test 测试方法，而是使用 CommandLineRunner 在应用启动时执行
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Component
public class MyBatisTestRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisTestRunner.class);

    @Resource
    private UserEntityMapper userEntityMapper;

    @Override
    public void run(String... args) throws Exception {
        logger.info("========== MyBatis 测试开始 ==========");

        try {
            // 测试1: 查询所有用户
            logger.info("【测试1】查询所有用户");
            List<UserEntity> allUsers = userEntityMapper.selectList(null);
            logger.info("查询结果数量: {}", allUsers.size());
            allUsers.forEach(user -> {
                logger.info("用户信息 - ID: {}, 姓名: {}, 电话: {}, 年龄: {}, 邮箱: {}",
                    user.getId(), user.getName(), user.getPhone(), user.getAge(), user.getEmail());
            });

            // 测试2: 根据ID查询用户
            logger.info("\n【测试2】根据ID查询用户");
            if (!allUsers.isEmpty()) {
                Long firstUserId = allUsers.get(0).getId();
                UserEntity userById = userEntityMapper.selectById(firstUserId);
                if (userById != null) {
                    logger.info("查询成功 - ID: {}, 姓名: {}, 电话: {}, 年龄: {}, 邮箱: {}",
                        userById.getId(), userById.getName(), userById.getPhone(),
                        userById.getAge(), userById.getEmail());
                } else {
                    logger.warn("未找到ID为 {} 的用户", firstUserId);
                }
            }

            // 测试3: 插入新用户
            logger.info("\n【测试3】插入新用户");
            UserEntity newUser = new UserEntity();
            newUser.setName("测试用户");
            newUser.setPhone("13900139000");
            newUser.setAge(25);
            newUser.setEmail("test@example.com");

            int insertResult = userEntityMapper.insert(newUser);
            logger.info("插入结果: {} (影响行数: {})", insertResult > 0 ? "成功" : "失败", insertResult);
            if (insertResult > 0) {
                logger.info("新用户ID: {}", newUser.getId());
            }

            // 测试4: 更新用户信息
            logger.info("\n【测试4】更新用户信息");
            if (newUser.getId() != null) {
                newUser.setName("更新的测试用户");
                newUser.setAge(26);
                int updateResult = userEntityMapper.updateById(newUser);
                logger.info("更新结果: {} (影响行数: {})", updateResult > 0 ? "成功" : "失败", updateResult);

                // 验证更新结果
                UserEntity updatedUser = userEntityMapper.selectById(newUser.getId());
                if (updatedUser != null) {
                    logger.info("更新后用户信息 - ID: {}, 姓名: {}, 年龄: {}",
                        updatedUser.getId(), updatedUser.getName(), updatedUser.getAge());
                }
            }

            // 测试5: 查询更新后的所有用户
            logger.info("\n【测试5】查询更新后的所有用户");
            List<UserEntity> allUsersAfterUpdate = userEntityMapper.selectList(null);
            logger.info("更新后用户总数: {}", allUsersAfterUpdate.size());
            allUsersAfterUpdate.forEach(user -> {
                logger.info("用户信息 - ID: {}, 姓名: {}, 电话: {}, 年龄: {}, 邮箱: {}",
                    user.getId(), user.getName(), user.getPhone(), user.getAge(), user.getEmail());
            });

            // 测试6: 删除用户（可选，注释掉避免删除测试数据）
            /*
            logger.info("\n【测试6】删除用户");
            if (newUser.getId() != null) {
                int deleteResult = userEntityMapper.deleteById(newUser.getId());
                logger.info("删除结果: {} (影响行数: {})", deleteResult > 0 ? "成功" : "失败", deleteResult);
            }
            */

            logger.info("\n========== MyBatis 测试完成 ==========");
            logger.info("所有测试操作执行完成！");

        } catch (Exception e) {
            logger.error("MyBatis 测试执行出错", e);
            throw e;
        }
    }
}

