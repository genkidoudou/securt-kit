package com.example.mapper;

import com.example.dto.UserOrderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户订单多表查询 Mapper 接口
 * 
 * @author hexlodev
 * @since 1.0.0
 */
@Mapper
public interface UserOrderMapper {
    
    /**
     * 查询用户及其所有订单（内连接）
     * 测试多表查询时的字段加密解密
     */
    List<UserOrderDTO> selectUserWithOrders(@Param("userId") Long userId);
    
    /**
     * 查询所有用户及其订单（左连接）
     * 测试多表查询时包含NULL的情况
     */
    List<UserOrderDTO> selectAllUsersWithOrders();
    
    /**
     * 根据订单号查询用户和订单信息
     * 测试多表查询的条件筛选
     */
    UserOrderDTO selectUserOrderByOrderNo(@Param("orderNo") String orderNo);
    
    /**
     * 查询订单及其用户信息（从订单表出发）
     * 测试反向多表查询
     */
    List<UserOrderDTO> selectOrdersWithUsers();
    
    /**
     * 统计每个用户的订单数量和总金额
     * 测试多表查询与聚合函数
     */
    List<UserOrderDTO> selectUserOrderStats();
}

