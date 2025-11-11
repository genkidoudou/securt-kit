-- 创建用户表
CREATE TABLE IF NOT EXISTS "user" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    age INT,
    email VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建订单表
CREATE TABLE IF NOT EXISTS "orders" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100),
    customer_phone VARCHAR(20),
    amount DECIMAL(10, 2),
    status VARCHAR(20),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES "user"(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_name ON "user"(name);
CREATE INDEX IF NOT EXISTS idx_user_email ON "user"(email);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON "orders"(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_no ON "orders"(order_no);

