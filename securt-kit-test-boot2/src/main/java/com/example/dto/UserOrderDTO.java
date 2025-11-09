package com.example.dto;

import java.math.BigDecimal;

/**
 * 用户订单查询结果 DTO
 * 用于多表查询结果映射
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class UserOrderDTO {
    // 用户字段
    private Long userId;
    private String userName;
    private String userPhone;
    private Integer userAge;
    private String userEmail;
    
    // 订单字段
    private Long orderId;
    private String orderNo;
    private String customerName;
    private String customerPhone;
    private BigDecimal amount;
    private String orderStatus;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public Integer getUserAge() {
        return userAge;
    }

    public void setUserAge(Integer userAge) {
        this.userAge = userAge;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    @Override
    public String toString() {
        return "UserOrderDTO{" +
            "userId=" + userId +
            ", userName='" + userName + '\'' +
            ", userPhone='" + userPhone + '\'' +
            ", userAge=" + userAge +
            ", userEmail='" + userEmail + '\'' +
            ", orderId=" + orderId +
            ", orderNo='" + orderNo + '\'' +
            ", customerName='" + customerName + '\'' +
            ", customerPhone='" + customerPhone + '\'' +
            ", amount=" + amount +
            ", orderStatus='" + orderStatus + '\'' +
            '}';
    }
}

