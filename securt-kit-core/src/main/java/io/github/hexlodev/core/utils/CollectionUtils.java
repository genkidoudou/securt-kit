package io.github.hexlodev.core.utils;

import io.github.hexlodev.core.parser.constant.SymbolConstant;

import java.util.*;

/**
 * 集合工具类（自定义扩展）
 * <p>
 * 注意：本类仅包含项目特定的自定义方法。对于通用的集合操作（如 isEmpty、isNotEmpty、size），
 * 请使用 {@link cn.hutool.core.collection.CollectionUtil}。
 * </p>
 * <p>
 * 已迁移到 Hutool 的方法：
 * <ul>
 *   <li>{@code isEmpty(Collection)} → {@link cn.hutool.core.collection.CollectionUtil#isEmpty}</li>
 *   <li>{@code isNotEmpty(Collection)} → {@link cn.hutool.core.collection.CollectionUtil#isNotEmpty}</li>
 *   <li>{@code isEmpty(Map)} → {@link cn.hutool.core.collection.CollectionUtil#isEmpty}</li>
 *   <li>{@code isNotEmpty(Map)} → {@link cn.hutool.core.collection.CollectionUtil#isNotEmpty}</li>
 *   <li>{@code size(Collection)} → {@link cn.hutool.core.collection.CollectionUtil#size}</li>
 *   <li>{@code size(Map)} → {@link cn.hutool.core.collection.CollectionUtil#size}</li>
 * </ul>
 * </p>
 *
 * @author chu7
 * @date 2025/5/26 11:28
 * @since 1.0.0
 */
public class CollectionUtils {

    /**
     * 忽略反引号(`)和双引号(")，从map中获取值
     * 主要用于处理数据库字段名可能被反引号或双引号包围的情况
     * 会尝试多种方式查找：原始key、去除符号的key、添加符号的key
     *
     * @param map 要查找的Map
     * @param key 查找的key
     * @param <T> 值的类型
     * @return 找到的值，如果未找到则返回null
     * @author liutangqi
     * @date 2025/5/30 11:24
     */
    public static <T> T getValueIgnoreFloat(Map<String, T> map, String key) {
        if (null == map) {
            return null;
        }
        T value = map.get(key);
        //1.找到了直接返回
        if (value != null) {
            return value;
        }

        //2.去除 ` 和 " 进行查询
        if (key.startsWith(SymbolConstant.FLOAT)) {
            return map.get(StringUtils.trim(key, SymbolConstant.FLOAT));
        }
        if (key.startsWith(SymbolConstant.DOUBLE_QUOTES)) {
            return map.get(StringUtils.trim(key, SymbolConstant.DOUBLE_QUOTES));
        }

        //3.按照添加` " 的方式去查询
        if (value == null) {
            value = map.get(SymbolConstant.FLOAT + key.trim() + SymbolConstant.FLOAT);
        }
        if (value == null) {
            value = map.get(SymbolConstant.DOUBLE_QUOTES + key.trim() + SymbolConstant.DOUBLE_QUOTES);
        }
        return value;
    }

}