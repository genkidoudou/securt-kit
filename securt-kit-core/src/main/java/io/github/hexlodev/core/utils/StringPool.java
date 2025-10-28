package io.github.hexlodev.core.utils;

/**
 * 字符串常量池
 * 
 * <p>这个类提供了SQL解析过程中常用的字符串常量，主要用于 {@link TableNameParser} 类。
 * 通过集中管理这些常量，可以提高代码的可维护性和一致性。</p>
 * 
 * <p>包含的常量：</p>
 * <ul>
 *   <li>括号：左括号 {@code (} 和右括号 {@code )}</li>
 *   <li>分隔符：逗号 {@code ,} 和分号 {@code ;}</li>
 *   <li>通配符：星号 {@code *}</li>
 *   <li>点号：用于表名限定符 {@code .}</li>
 *   <li>空白字符：空格和空字符串</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 检查字符串是否包含左括号
 * if (sql.contains(StringPool.LEFT_BRACKET)) {
 *     // 处理括号
 * }
 * 
 * // 分割SQL语句
 * String[] parts = sql.split(StringPool.COMMA);
 * }</pre>
 * 
 * @author hexlodev
 * @since 1.0.0
 * @see TableNameParser
 */
public final class StringPool {
    
    /** 左括号 */
    public static final String LEFT_BRACKET = "(";
    
    /** 右括号 */
    public static final String RIGHT_BRACKET = ")";
    
    /** 逗号分隔符 */
    public static final String COMMA = ",";
    
    /** 星号通配符 */
    public static final String ASTERISK = "*";
    
    /** 点号，用于表名限定符 */
    public static final String DOT = ".";
    
    /** 分号 */
    public static final String SEMICOLON = ";";
    
    /** 空格 */
    public static final String SPACE = " ";
    
    /** 空字符串 */
    public static final String EMPTY = "";
    
    /**
     * 私有构造函数
     * 
     * <p>工具类不允许实例化，所有方法都是静态的。</p>
     */
    private StringPool() {
        // 工具类，不允许实例化
    }
}