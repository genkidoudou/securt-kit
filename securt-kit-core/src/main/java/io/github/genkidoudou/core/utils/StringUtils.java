package io.github.genkidoudou.core.utils;

import cn.hutool.core.util.StrUtil;
import io.github.genkidoudou.core.parser.constant.SymbolConstant;

/**
 * 字符串工具类（自定义扩展）
 * <p>
 * 注意：本类仅包含项目特定的自定义方法。对于通用的字符串操作（如 isBlank、equalsIgnoreCase），
 * 请使用 {@link cn.hutool.core.util.StrUtil}。
 * </p>
 * <p>
 * 已迁移到 Hutool 的方法：
 * <ul>
 *   <li>{@code isBlank(String)} → {@link cn.hutool.core.util.StrUtil#isBlank}</li>
 *   <li>{@code equalCaseInsensitive(String, String)} → {@link cn.hutool.core.util.StrUtil#equalsIgnoreCase}</li>
 * </ul>
 * </p>
 * 
 * @author chu7
 * @date 2025/5/26 11:28
 * @since 1.0.0
 */
public class StringUtils {
    /**
     * 去除字符串开头和结尾的指定字符
     * 支持重复去除，例如去除字符串"abcabc"开头和结尾的"abc"会得到空字符串
     *
     * @param str 待处理的字符串
     * @param c 要去除的字符或字符串
     * @return 去除指定字符后的字符串，如果输入参数无效则返回原字符串
     * @author liutangqi
     * @date 2025/5/30 11:24
     */
    public static String trim(String str, String c) {
        if (str == null || c == null || c.isEmpty() || str.isEmpty()) {
            return str;
        }

        int str1Len = str.length();
        int str2Len = c.length();

        // 如果 str2 比 str1 长，不可能匹配
        if (str2Len > str1Len) {
            return str;
        }

        int start = 0;
        int end = str1Len;

        // 处理开头的 str2 重复匹配
        while (start <= end - str2Len && str.startsWith(c, start)) {
            start += str2Len;
        }

        // 处理结尾的 str2 重复匹配
        while (end >= start + str2Len && str.startsWith(c, end - str2Len)) {
            end -= str2Len;
        }

        return (start > 0 || end < str1Len) ? str.substring(start, end) : str;
    }

    /**
     * 忽略大小写，忽略开头结尾的反引号(`)和双引号(")判断两个字段是否相等
     * 主要用于数据库字段名的比较，因为字段名可能被反引号或双引号包围
     * 
     * @param a 第一个字段名
     * @param b 第二个字段名
     * @return 如果去除符号并忽略大小写后两个字段名相等则返回true，否则返回false
     */
    public static boolean equalIgnoreFieldSymbol(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) {
            return false;
        }
        //去掉首尾的 ` 、 "
        String clearA = trim(trim(a, SymbolConstant.FLOAT), SymbolConstant.DOUBLE_QUOTES);
        String clearB = trim(trim(b, SymbolConstant.FLOAT), SymbolConstant.DOUBLE_QUOTES);
        return StrUtil.equalsIgnoreCase(clearA, clearB);
    }
}
