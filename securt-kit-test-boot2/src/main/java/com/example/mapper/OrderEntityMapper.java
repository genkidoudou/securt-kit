package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper 接口
 * 
 * @author hexlodev
 * @since 1.0.0
 */
@Mapper
public interface OrderEntityMapper extends BaseMapper<OrderEntity> {
}

