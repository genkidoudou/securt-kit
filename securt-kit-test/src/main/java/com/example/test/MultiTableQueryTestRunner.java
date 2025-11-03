package com.example.test;

import com.example.dto.UserOrderDTO;
import com.example.entity.OrderEntity;
import com.example.entity.UserEntity;
import com.example.mapper.OrderEntityMapper;
import com.example.mapper.UserEntityMapper;
import com.example.mapper.UserOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * 多表查询测试类
 * 测试在多表查询场景下字段加密解密功能是否正常工作
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Component
@Order(2) // 在单表测试之后执行
public class MultiTableQueryTestRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MultiTableQueryTestRunner.class);

    @Resource
    private UserEntityMapper userEntityMapper;

    @Resource
    private OrderEntityMapper orderEntityMapper;

    @Resource
    private UserOrderMapper userOrderMapper;

    @Override
    public void run(String... args) throws Exception {
        logger.info("\n========== 多表查询测试开始 ==========");

        try {
            // 测试1: 内连接查询 - 查询指定用户的所有订单
            logger.info("\n【多表测试1】内连接查询 - 查询用户ID=1的所有订单");
            List<UserOrderDTO> userOrders = userOrderMapper.selectUserWithOrders(1L);
            logger.info("查询结果数量: {}", userOrders.size());
            userOrders.forEach(dto -> {
                logger.info("用户订单信息 - 用户ID: {}, 用户名: {}, 用户电话: {}, 订单号: {}, 客户名: {}, 客户电话: {}, 金额: {}, 状态: {}",
                    dto.getUserId(), dto.getUserName(), dto.getUserPhone(),
                    dto.getOrderNo(), dto.getCustomerName(), dto.getCustomerPhone(),
                    dto.getAmount(), dto.getOrderStatus());
                // 验证加密字段是否正确解密
                verifyDecryption("用户表-name", dto.getUserName());
                verifyDecryption("用户表-phone", dto.getUserPhone());
                verifyDecryption("订单表-customer_name", dto.getCustomerName());
                verifyDecryption("订单表-customer_phone", dto.getCustomerPhone());
            });

            // 测试2: 左连接查询 - 查询所有用户及其订单（包含没有订单的用户）
            logger.info("\n【多表测试2】左连接查询 - 查询所有用户及其订单");
            List<UserOrderDTO> allUsersWithOrders = userOrderMapper.selectAllUsersWithOrders();
            logger.info("查询结果数量: {}", allUsersWithOrders.size());
            allUsersWithOrders.forEach(dto -> {
                if (dto.getOrderId() != null) {
                    logger.info("用户订单 - 用户: {} ({}), 订单: {} ({})",
                        dto.getUserName(), dto.getUserPhone(),
                        dto.getOrderNo(), dto.getCustomerName());
                    verifyDecryption("用户表-name", dto.getUserName());
                    verifyDecryption("用户表-phone", dto.getUserPhone());
                    verifyDecryption("订单表-customer_name", dto.getCustomerName());
                } else {
                    logger.info("用户无订单 - 用户: {} ({})", dto.getUserName(), dto.getUserPhone());
                    verifyDecryption("用户表-name", dto.getUserName());
                    verifyDecryption("用户表-phone", dto.getUserPhone());
                }
            });

            // 测试3: 根据订单号查询
            logger.info("\n【多表测试3】根据订单号查询用户和订单信息");
            UserOrderDTO orderInfo = userOrderMapper.selectUserOrderByOrderNo("ORD20250101001");
            if (orderInfo != null) {
                logger.info("订单信息 - 订单号: {}, 用户: {} ({}), 客户: {} ({}), 金额: {}, 状态: {}",
                    orderInfo.getOrderNo(),
                    orderInfo.getUserName(), orderInfo.getUserPhone(),
                    orderInfo.getCustomerName(), orderInfo.getCustomerPhone(),
                    orderInfo.getAmount(), orderInfo.getOrderStatus());
                verifyDecryption("用户表-name", orderInfo.getUserName());
                verifyDecryption("用户表-phone", orderInfo.getUserPhone());
                verifyDecryption("订单表-customer_name", orderInfo.getCustomerName());
                verifyDecryption("订单表-customer_phone", orderInfo.getCustomerPhone());
            } else {
                logger.warn("未找到订单号: ORD20250101001");
            }

            // 测试4: 从订单表出发查询用户信息
            logger.info("\n【多表测试4】从订单表出发查询用户信息");
            List<UserOrderDTO> ordersWithUsers = userOrderMapper.selectOrdersWithUsers();
            logger.info("查询结果数量: {}", ordersWithUsers.size());
            ordersWithUsers.forEach(dto -> {
                logger.info("订单用户 - 订单号: {}, 用户: {} ({}), 客户: {} ({})",
                    dto.getOrderNo(),
                    dto.getUserName(), dto.getUserPhone(),
                    dto.getCustomerName(), dto.getCustomerPhone());
                verifyDecryption("用户表-name", dto.getUserName());
                verifyDecryption("用户表-phone", dto.getUserPhone());
                verifyDecryption("订单表-customer_name", dto.getCustomerName());
                verifyDecryption("订单表-customer_phone", dto.getCustomerPhone());
            });

            // 测试5: 统计查询（带聚合函数）
            logger.info("\n【多表测试5】统计每个用户的订单数量和总金额");
            List<UserOrderDTO> userStats = userOrderMapper.selectUserOrderStats();
            logger.info("统计结果数量: {}", userStats.size());
            userStats.forEach(dto -> {
                logger.info("用户统计 - 用户: {} ({}), 订单数量: {}, 总金额: {}",
                    dto.getUserName(), dto.getUserPhone(),
                    dto.getOrderId(), dto.getAmount());
                verifyDecryption("用户表-name", dto.getUserName());
                verifyDecryption("用户表-phone", dto.getUserPhone());
            });

            // 测试6: 插入新订单（测试加密）
            logger.info("\n【多表测试6】插入新订单（测试字段加密）");
            List<UserEntity> users = userEntityMapper.selectList(null);
            if (!users.isEmpty()) {
                Long userId = users.get(0).getId();
                
                OrderEntity newOrder = new OrderEntity();
                newOrder.setUserId(userId);
                newOrder.setOrderNo("ORD20250101999");
                newOrder.setCustomerName("测试客户(加密)");
                newOrder.setCustomerPhone("13999999999(加密)");
                newOrder.setAmount(new BigDecimal("999.99"));
                newOrder.setStatus("PENDING");
                
                int insertResult = orderEntityMapper.insert(newOrder);
                logger.info("插入订单结果: {} (影响行数: {})", insertResult > 0 ? "成功" : "失败", insertResult);
                if (insertResult > 0) {
                    logger.info("新订单ID: {}", newOrder.getId());
                    
                    // 查询验证加密是否生效
                    OrderEntity insertedOrder = orderEntityMapper.selectById(newOrder.getId());
                    if (insertedOrder != null) {
                        logger.info("插入后查询 - 订单号: {}, 客户名: {}, 客户电话: {}",
                            insertedOrder.getOrderNo(), insertedOrder.getCustomerName(), insertedOrder.getCustomerPhone());
                        verifyDecryption("订单表-customer_name", insertedOrder.getCustomerName());
                        verifyDecryption("订单表-customer_phone", insertedOrder.getCustomerPhone());
                    }
                }
            }

            logger.info("\n========== 多表查询测试完成 ==========");
            logger.info("所有多表查询测试操作执行完成！");

        } catch (Exception e) {
            logger.error("多表查询测试执行出错", e);
            throw e;
        }
    }

    /**
     * 验证字段是否正确解密
     * 如果字段被加密，数据库中存储的是加密后的值，查询时应该自动解密
     * 
     * @param fieldDesc 字段描述
     * @param fieldValue 字段值
     */
    private void verifyDecryption(String fieldDesc, String fieldValue) {
        if (fieldValue == null) {
            logger.debug("字段 {} 值为 null，跳过验证", fieldDesc);
            return;
        }
        
        // 如果值包含加密标记或明显是加密后的格式，说明解密可能失败
        // 这里只是简单的验证，实际应该根据加密策略判断
        if (fieldValue.contains("(加密)") || fieldValue.length() > 100) {
            logger.warn("字段 {} 可能未正确解密，值: {}", fieldDesc, fieldValue);
        } else {
            logger.debug("字段 {} 解密验证通过，值: {}", fieldDesc, fieldValue);
        }
    }
}

