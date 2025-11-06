package io.github.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.test.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单Mapper接口
 * 
 * <p>提供订单相关的数据库操作方法，包括基础 CRUD 和复杂查询。</p>
 * 
 * <p><b>功能说明：</b></p>
 * <ul>
 *   <li>继承 <code>BaseMapper&lt;OrderEntity&gt;</code>，提供基础的 CRUD 方法</li>
 *   <li>提供 JOIN 查询方法，用于测试复杂 SQL 场景</li>
 *   <li>提供聚合查询方法，用于测试聚合函数场景</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @Autowired
 * private OrderEntityMapper orderMapper;
 * 
 * // JOIN 查询：根据用户ID查询订单
 * @DS("primary")
 * public List<OrderEntity> getOrdersByUserId(Long userId) {
 *     return orderMapper.selectOrdersByUserId(userId);
 * }
 * 
 * // 聚合查询：统计订单数量
 * @DS("primary")
 * public Long countOrders(Long userId) {
 *     return orderMapper.countOrdersByUserId(userId);
 * }
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.1.0
 * @see io.github.test.entity.OrderEntity
 * @see io.github.test.service.ComplexQueryService
 */
@Mapper
public interface OrderEntityMapper extends BaseMapper<OrderEntity> {
    
    /**
     * 根据用户ID查询订单列表（JOIN查询）
     * 
     * <p>使用 INNER JOIN 关联 user 表，查询指定用户的所有订单。</p>
     * 
     * <p><b>SQL 示例：</b></p>
     * <pre>{@code
     * SELECT o.id, o.user_id, o.order_no, o.amount, o.address, o.status
     * FROM "order" o
     * INNER JOIN "user" u ON o.user_id = u.id
     * WHERE u.id = ?
     * }</pre>
     * 
     * <p><b>测试场景：</b></p>
     * <ul>
     *   <li>JOIN 查询场景</li>
     *   <li>WHERE 条件中包含加密字段的查询</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 订单列表
     */
    @Select("SELECT o.id, o.user_id, o.order_no, o.amount, o.address, o.status " +
            "FROM \"order\" o " +
            "INNER JOIN \"user\" u ON o.user_id = u.id " +
            "WHERE u.id = #{userId}")
    List<OrderEntity> selectOrdersByUserId(@Param("userId") Long userId);
    
    /**
     * 查询订单总数（聚合查询）
     * 
     * <p>使用 COUNT 聚合函数统计指定用户的订单数量。</p>
     * 
     * <p><b>SQL 示例：</b></p>
     * <pre>{@code
     * SELECT COUNT(*) FROM "order" WHERE user_id = ?
     * }</pre>
     * 
     * <p><b>测试场景：</b></p>
     * <ul>
     *   <li>聚合函数场景</li>
     *   <li>COUNT 函数使用</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 订单总数
     */
    @Select("SELECT COUNT(*) FROM \"order\" WHERE user_id = #{userId}")
    Long countOrdersByUserId(@Param("userId") Long userId);
    
    /**
     * 查询订单金额总和（聚合查询）
     * 
     * <p>使用 SUM 聚合函数计算指定用户的订单金额总和。</p>
     * 
     * <p><b>SQL 示例：</b></p>
     * <pre>{@code
     * SELECT SUM(CAST(amount AS DECIMAL)) FROM "order" WHERE user_id = ?
     * }</pre>
     * 
     * <p><b>测试场景：</b></p>
     * <ul>
     *   <li>聚合函数场景</li>
     *   <li>SUM 函数使用</li>
     *   <li>类型转换（CAST）</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 订单金额总和
     */
    @Select("SELECT SUM(CAST(amount AS DECIMAL)) FROM \"order\" WHERE user_id = #{userId}")
    Double sumAmountByUserId(@Param("userId") Long userId);
}

