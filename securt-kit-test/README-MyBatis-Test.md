# MyBatis 测试类使用说明

## 概述

本项目创建了一个基于 Spring Boot 和 MyBatis 的测试类，**不使用测试方法（@Test）**，而是通过 `CommandLineRunner` 在应用启动时自动执行。

## 特点

- ✅ **手动配置 MyBatis**：不使用自动配置，完全手动配置
- ✅ **配置集中管理**：所有配置都在 `application.yml` 文件中
- ✅ **真实测试环境**：使用 H2 内存数据库，模拟真实环境
- ✅ **不使用测试方法**：通过 `CommandLineRunner` 执行测试逻辑

## 项目结构

```
securt-kit-test/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/example/
│   │   │   │   ├── TestApplication.java          # Spring Boot 主应用类
│   │   │   │   ├── config/
│   │   │   │   │   └── ManualMyBatisConfig.java  # 手动 MyBatis 配置类
│   │   │   │   └── test/
│   │   │   │       └── MyBatisTestRunner.java    # 测试执行类（CommandLineRunner）
│   │   │   └── io/github/test/
│   │   │       ├── entity/
│   │   │       │   └── UserEntity.java           # 用户实体类
│   │   │       └── mapper/
│   │   │           └── UserEntityMapper.java     # MyBatis Mapper 接口
│   │   └── resources/
│   │       ├── application.yml                   # 主配置文件（包含所有配置）
│   │       ├── mybatis-config.xml                # MyBatis 配置文件
│   │       ├── schema.sql                        # 数据库表结构脚本
│   │       ├── data.sql                          # 初始化数据脚本
│   │       └── mapper/
│   │           └── UserMapper.xml                # MyBatis Mapper XML
│   └── test/
│       └── ...                                   # 测试目录（保留原有测试）
└── pom.xml
```

## 配置说明

### application.yml

所有配置都集中在 `application.yml` 文件中：

1. **Spring Boot 配置**
   - 应用名称
   - 数据源配置（H2 数据库）
   - SQL 初始化配置

2. **MyBatis 配置**
   - 配置文件路径
   - Mapper XML 文件路径
   - 实体类包路径
   - MyBatis 详细配置项

3. **MyBatis-Plus 配置**
   - Mapper XML 文件路径
   - 实体类包路径
   - 全局配置

4. **日志配置**
   - 日志级别设置
   - 日志格式配置

### 手动配置类

`ManualMyBatisConfig.java` 负责手动配置：

- 数据源（从 `application.yml` 读取）
- SqlSessionFactory（手动创建）
- MyBatis-Plus 拦截器
- 事务管理器

## 运行方式

### 方式1：使用 Maven 运行

```bash
cd securt-kit-test
mvn clean compile
mvn spring-boot:run
```

### 方式2：使用 IDE 运行

直接运行 `com.example.TestApplication` 类的 `main` 方法。

### 方式3：打包后运行

```bash
mvn clean package
java -jar target/securt-kit-test-0.0.1-SNAPSHOT.jar
```

## 测试内容

测试类 `MyBatisTestRunner` 会执行以下测试操作：

1. **查询所有用户**：测试基础查询功能
2. **根据ID查询用户**：测试单个记录查询
3. **插入新用户**：测试数据插入功能
4. **更新用户信息**：测试数据更新功能
5. **查询更新后的数据**：验证更新是否成功

所有测试结果都会输出到控制台日志中。

## 数据库配置

使用 H2 内存数据库，配置如下：

- **驱动**：`org.h2.Driver`
- **URL**：`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL`
- **用户名**：`sa`
- **密码**：空

数据库表结构在 `schema.sql` 中定义，初始化数据在 `data.sql` 中定义。

## 注意事项

1. **禁用自动配置**：主应用类中禁用了 `DataSourceAutoConfiguration`，使用手动配置
2. **依赖作用域**：MyBatis 相关依赖已经从 `test` 作用域移到 `compile` 作用域
3. **配置文件优先级**：`application.yml` 中的配置会被手动配置类读取和使用
4. **日志输出**：MyBatis SQL 日志会输出到控制台（STDOUT_LOGGING）

## 扩展说明

如果需要添加更多测试功能：

1. 在 `MyBatisTestRunner` 的 `run` 方法中添加测试逻辑
2. 如果需要新的 Mapper，在 `io.github.test.mapper` 包下创建接口
3. 在 `mapper` 目录下创建对应的 XML 文件
4. 确保在 `application.yml` 中配置了正确的包路径

## 故障排查

如果遇到问题，请检查：

1. ✅ 依赖是否已正确下载（运行 `mvn dependency:tree`）
2. ✅ `application.yml` 配置是否正确
3. ✅ Mapper XML 文件路径是否正确
4. ✅ 实体类包路径是否正确
5. ✅ 日志输出中是否有错误信息

