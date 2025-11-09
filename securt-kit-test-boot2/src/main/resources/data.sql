-- 初始化测试数据
INSERT INTO "user" (name, phone, age, email) VALUES 
('张三(加密)', '13800138000(加密)', 25, 'zhangsan@example.com'),
('李四(加密)', '13800138001(加密)', 30, 'lisi@example.com'),
('王五(加密)', '13800138002(加密)', 28, 'wangwu@example.com')
ON DUPLICATE KEY UPDATE name=name;

-- 初始化订单测试数据（在用户数据插入后）
INSERT INTO orders (user_id, order_no, customer_name, customer_phone, amount, status) VALUES 
(1, 'ORD20250101001', '张三(加密)', '13800138000(加密)', 199.99, 'PAID'),
(1, 'ORD20250101002', '张三(加密)', '13800138000(加密)', 299.99, 'SHIPPED'),
(2, 'ORD20250101003', '李四(加密)', '13800138001(加密)', 599.99, 'DELIVERED'),
(2, 'ORD20250101004', '李四(加密)', '13800138001(加密)', 399.99, 'PAID'),
(3, 'ORD20250101005', '王五(加密)', '13800138002(加密)', 99.99, 'PENDING')
ON DUPLICATE KEY UPDATE order_no=order_no;

