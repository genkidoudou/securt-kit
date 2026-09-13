# Securt-Kit 测试项目（Spring Boot 2.7）

## 简介

本测试项目演示了 Securt-Kit 在 Spring Boot 2.7.x 环境下的使用方法，包含完整的示例代码和配置。

## 项目结构

```
securt-kit-test-boot2/
├── src/main/java/com/example/
│   ├── TestApplication.java          # 启动类
│   ├── entity/                       # 实体类
│   │   ├── UserEntity.java
│   │   └── OrderEntity.java
│   ├── mapper/                       # MyBatis Mapper
│   │   ├── UserEntityMapper.java
│   │   ├── OrderEntityMapper.java
│   │   └── UserOrderMapper.java
│   ├── dto/                          # DTO 类
│   │   └── UserOrderDTO.java
│   ├── config/                       # 配置类
│   │   └── ManualMyBatisConfig.java
│   ├── MyFieldEncryptorStrategy.java # 自定义加密策略
│   └── test/                         # 测试运行器
│       ├── MyBatisTestRunner.java
│       └── MultiTableQueryTestRunner.java
└── src/main/resources/
    ├── application.yml                # 配置文件
    ├── schema.sql                    # 数据库表结构
    ├── data.sql                      # 初始数据
    └── mapper/                       # MyBatis XML
        ├── UserMapper.xml
        └── UserOrderMapper.xml
```

## 快速开始

### 1. 运行测试项目

```bash
cd securt-kit-test-boot2
mvn spring-boot:run
```

### 2. 访问监控界面

启动后访问：`http://localhost:8080/monitor/index.html`

### 2.1 访问 Playground 演示页

启动后访问：`http://localhost:8080/playground/`

默认页面是 `playground_person` 人员维护台：筛选、列表、新增/编辑/删除，并可在「业务视图（解密）」与「原始视图（库内密文）」间切换。手机号与身份证加密落库，摘要源为二者。

要求 JDBC 模式、`playground_person` 白名单、`phone`/`id_card` 加密、`phone+id_card -> row_digest` 摘要规则和 `SECURT_SKIP` 开启；页面顶部会显示前置检查结果。

**注意：** Playground 仅存在于本测试工程（依赖 `securt-kit-playground` + 本地 Servlet 装配），**不会**随 `securt-kit-starter-*` 发布。配置项：`securtkit.playground.enabled=true`。

### 3. 查看测试结果

项目启动后会自动运行测试，查看控制台输出。

## 配置说明

### 数据源配置

```yaml
spring:
  datasource:
    driver-class-name: io.github.genkidoudou.core.interceptor.SimpleInterceptorDriver
    url: jdbc:interceptor:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
```

### 加密配置

```yaml
securtkit:
  encryptor:
    enable: true
    tables:
      - table-name: user
        fields:
          - field-name: name
          - field-name: phone
      - table-name: orders
        fields:
          - field-name: customer_name
          - field-name: customer_phone
```

## 测试场景

### 1. 单表操作

- INSERT 操作：自动加密字段
- UPDATE 操作：自动加密字段
- SELECT 操作：自动解密字段

### 2. 多表查询

- JOIN 查询：支持多表关联查询
- 子查询：支持子查询场景

### 3. MyBatis 集成

- MyBatis XML Mapper
- MyBatis-Plus
- 参数绑定和结果映射

## 示例代码

### 实体类

```java
@Data
@TableName("user")
public class UserEntity {
    private Long id;
    private String name;      // 加密字段
    private String phone;      // 加密字段
    private String email;
}
```

### Mapper 接口

```java
@Mapper
public interface UserEntityMapper extends BaseMapper<UserEntity> {
    UserEntity selectById(Long id);
    int insert(UserEntity user);
    int updateById(UserEntity user);
}
```

### Service 使用

```java
@Service
public class UserService {
    @Autowired
    private UserEntityMapper userMapper;
    
    public void createUser(UserEntity user) {
        // name 和 phone 字段会自动加密
        userMapper.insert(user);
    }
    
    public UserEntity getUser(Long id) {
        // name 和 phone 字段会自动解密
        return userMapper.selectById(id);
    }
}
```

## 注意事项

1. **数据源 URL 必须使用 `jdbc:interceptor:` 前缀**
2. **表名和字段名必须与配置完全匹配**
3. **加密策略必须正确实现加密解密互逆**

## 相关文档

- [主 README](../../README.md)
- [使用指南](../../docs/USAGE.md)
- [快速开始](../../docs/QUICK-START.md)
