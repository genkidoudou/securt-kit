package io.github.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户实体类
 * 
 * <p>用于演示多数据源场景下的字段加密/解密功能。</p>
 * 
 * <p><b>加密配置说明：</b></p>
 * <ul>
 *   <li><b>主数据源（primary）</b>：加密 <code>name</code> 和 <code>phone</code> 字段</li>
 *   <li><b>从数据源（secondary）</b>：只加密 <code>name</code> 字段，<code>phone</code> 保持明文</li>
 *   <li><b>第三方数据源（third）</b>：不加密任何字段</li>
 * </ul>
 * 
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>H2 数据库中 <code>user</code> 是保留关键字，需要在 SQL 中使用双引号括起来</li>
 *   <li>MyBatis-Plus 不会自动添加双引号，所以这里直接使用带双引号的表名</li>
 *   <li>配置中的表名不需要双引号，系统会自动处理</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 在主数据源中保存用户（name 和 phone 会自动加密）
 * @DS("primary")
 * public void saveToPrimary(UserEntity user) {
 *     userMapper.insert(user);
 * }
 * 
 * // 在主数据源中查询用户（name 和 phone 会自动解密）
 * @DS("primary")
 * public UserEntity getFromPrimary(Long id) {
 *     return userMapper.selectById(id);
 * }
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.1.0
 * @see io.github.test.service.MultiDataSourceUserService
 * @see io.github.test.mapper.UserEntityMapper
 */
// H2 数据库中 user 是保留关键字，需要在 SQL 中使用双引号括起来
// MyBatis-Plus 不会自动添加双引号，所以这里直接使用带双引号的表名
@TableName("\"user\"")
public class UserEntity {
    /**
     * 用户ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户姓名
     * 
     * <p>加密配置：</p>
     * <ul>
     *   <li>主数据源（primary）：加密</li>
     *   <li>从数据源（secondary）：加密</li>
     *   <li>第三方数据源（third）：不加密</li>
     * </ul>
     */
    private String name;
    
    /**
     * 用户手机号
     * 
     * <p>加密配置：</p>
     * <ul>
     *   <li>主数据源（primary）：加密</li>
     *   <li>从数据源（secondary）：不加密（保持明文）</li>
     *   <li>第三方数据源（third）：不加密</li>
     * </ul>
     */
    private String phone;
    
    /**
     * 用户年龄（不加密）
     */
    private Integer age;
    
    /**
     * 用户邮箱（不加密）
     */
    private String email;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "UserEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phone='" + phone + '\'' +
                ", age=" + age +
                ", email='" + email + '\'' +
                '}';
    }
}

