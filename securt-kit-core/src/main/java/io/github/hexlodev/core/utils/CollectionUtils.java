package io.github.hexlodev.core.utils;


import io.github.hexlodev.core.parser.constant.SymbolConstant;

import java.util.*;

/**
 * 集合工具类
 *
 * @author chu7
 * @date 2025/5/26 11:28
 */
public class CollectionUtils {

    /**
     * 判断集合是否为空
     * 包括null和空集合的情况
     *
     * @param collection 待检查的集合
     * @return 如果集合为null或空集合则返回true，否则返回false
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否不为空
     * 与isEmpty方法相反
     *
     * @param collection 待检查的集合
     * @return 如果集合不为null且不为空集合则返回true，否则返回false
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断Map是否为空
     * 包括null和空Map的情况
     *
     * @param map 待检查的Map
     * @return 如果Map为null或空Map则返回true，否则返回false
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断Map是否不为空
     * 与isEmpty方法相反
     *
     * @param map 待检查的Map
     * @return 如果Map不为null且不为空Map则返回true，否则返回false
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 获取集合的大小，如果为null则返回0
     *
     * @param collection 待检查的集合
     * @return 集合的大小，如果集合为null则返回0
     */
    public static int size(Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    /**
     * 获取Map的大小，如果为null则返回0
     *
     * @param map 待检查的Map
     * @return Map的大小，如果Map为null则返回0
     */
    public static int size(Map<?, ?> map) {
        return map != null ? map.size() : 0;
    }

    /**
     * 判断两个List是否相等
     * 通过比较大小和包含关系来判断两个List是否相等
     * 注意：此方法不保证元素的顺序，只要两个List包含相同的元素且大小相同即认为相等
     *
     * @param listA 第一个List
     * @param listB 第二个List
     * @return 如果两个List大小相同且包含相同的元素则返回true，否则返回false
     * @author liutangqi
     * @date 2025/3/4 18:06
     */
    public static boolean equals(List<?> listA, List<?> listB) {
        return listA.size() == listB.size() && listA.containsAll(listB);
    }

    /**
     * 判断两个Map是否相等（只判断一层）
     * 比较两个Map的键值对是否完全相同，不进行深层比较
     *
     * @param mapA 第一个Map
     * @param mapB 第二个Map
     * @return 如果两个Map的键值对完全相同则返回true，否则返回false
     * @author liutangqi
     * @date 2025/3/4 18:18
     */
    public static boolean equals(Map<?, ?> mapA, Map<?, ?> mapB) {
        Set<?> keySetA = mapA.keySet();
        Set<?> keySetB = mapB.keySet();
        if (keySetA.size() != keySetB.size()) {
            return false;
        }

        for (Object key : keySetA) {
            if (!Objects.equals(mapA.get(key), mapB.get(key))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 在原有的List中，每个间隔插入分隔符
     * 例如：[1,2,3] 插入 "," 后变成 [1,",",2,",",3]
     *
     * @param lists     原始List
     * @param separator 分隔符
     * @param <T>       泛型类型
     * @return 插入分隔符后的新List
     * @author liutangqi
     * @date 2025/5/30 16:44
     */
    public static <T> List<T> join(List<T> lists, T separator) {
        List<T> res = new ArrayList<>();
        for (int i = 0; i < lists.size(); i++) {
            res.add(lists.get(i));
            if (i != lists.size() - 1) {
                res.add(separator);
            }
        }
        return res;
    }

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

    /**
     * 从Map中获取值，获取成功后，再将该值给移除掉
     * 这是一个原子操作，先获取值再移除key
     * 注意：即使value为null，只要key存在也会被移除
     *
     * @param map 要操作的Map
     * @param key 要获取和移除的key
     * @param <K> key的类型
     * @param <V> value的类型
     * @return 获取到的值，如果key不存在则返回null
     * @author liutangqi
     * @date 2025/7/18 10:58
     */
    public static <K, V> V getAndRemove(Map<K, V> map, K key) {
        //1.先获取值
        V res = map.get(key);

        //2.判断Map中是否包含此key，包含就移除（注意：这里不能判断上面get的值是否为null来作为移除依据，因为Map中可以存null值作为value）
        if (map.containsKey(key)) {
            map.remove(key);
        }
        return res;
    }
}