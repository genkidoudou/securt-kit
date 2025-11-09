package io.github.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 订单实体类
 * 
 * <p>用于演示多数据源场景下的复杂查询（如 JOIN 查询）功能。</p>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>H2 数据库中 <code>order</code> 是保留关键字，需要在 SQL 中使用双引号括起来</li>
 *   <li>MyBatis-Plus 不会自动添加双引号，所以这里直接使用带双引号的表名</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // JOIN 查询：根据用户名查询订单
 * @DS("primary")
 * public List<OrderEntity> findOrdersByUserName(String userName) {
 *     // 先根据用户名查询用户ID（WHERE 条件中包含加密字段）
 *     UserEntity user = userMapper.selectOne(
 *         Wrappers.<UserEntity>lambdaQuery()
 *             .eq(UserEntity::getName, userName)
 *     );
 *     // 然后查询该用户的订单
 *     return orderMapper.selectOrdersByUserId(user.getId());
 * }
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.1.0
 * @see io.github.test.service.ComplexQueryService
 * @see io.github.test.mapper.OrderEntityMapper
 */
@TableName("\"order\"")
public class OrderEntity {
    /**
     * 订单ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID（外键，关联 user 表）
     */
    private Long userId;
    
    /**
     * 订单编号
     */
    private String orderNo;
    
    /**
     * 订单金额
     */
    private String amount;
    
    /**
     * 收货地址
     */
    private String address;
    
    /**
     * 订单状态（如：PENDING、PAID、SHIPPED、COMPLETED）
     */
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OrderEntity{" +
                "id=" + id +
                ", userId=" + userId +
                ", orderNo='" + orderNo + '\'' +
                ", amount='" + amount + '\'' +
                ", address='" + address + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

