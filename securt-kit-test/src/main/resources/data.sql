-- 初始化测试数据
INSERT INTO user (name, phone, age, email) VALUES 
('张三(加密)', '13800138000(加密)', 25, 'zhangsan@example.com'),
('李四(加密)', '13800138001(加密)', 30, 'lisi@example.com'),
('王五(加密)', '13800138002(加密)', 28, 'wangwu@example.com')
ON DUPLICATE KEY UPDATE name=name;

