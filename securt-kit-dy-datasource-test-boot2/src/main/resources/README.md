# 多数据源测试应用

## 启动方式

### 方式1：使用 Maven 运行
```bash
cd securt-kit-dy-datasource-test
mvn spring-boot:run
```

### 方式2：打包后运行
```bash
cd securt-kit-dy-datasource-test
mvn clean package
java -jar target/securt-kit-dy-datasource-test-1.0-SNAPSHOT.jar
```

### 方式3：在 IDE 中运行
直接运行 `io.github.test.MultiDataSourceApplication` 类的 `main` 方法

## 测试接口

应用启动后，访问以下接口进行测试：

1. **测试主数据源**（加密 name 和 phone）：
   ```
   GET http://localhost:8081/test/primary
   ```

2. **测试从数据源**（只加密 name）：
   ```
   GET http://localhost:8081/test/secondary
   ```

3. **测试第三方数据源**（不加密）：
   ```
   GET http://localhost:8081/test/third
   ```

4. **测试所有数据源**：
   ```
   GET http://localhost:8081/test/all
   ```

## 配置说明

### 数据源配置
- **primary**：主数据源，加密 `name` 和 `phone` 字段
- **secondary**：从数据源，只加密 `name` 字段
- **third**：第三方数据源，不加密任何字段

### 加密配置
配置在 `application.yml` 的 `securtkit.encryptor.tables` 中：
- 每个表配置可以指定 `datasource-id` 来标识所属数据源
- 如果不指定 `datasource-id`，则应用到所有数据源

## 验证加密效果

1. 访问测试接口，查看返回的数据
2. 检查日志，查看加密/解密过程
3. 直接查询数据库（绕过拦截器），验证数据是否被加密存储

