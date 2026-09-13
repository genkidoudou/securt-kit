package io.github.genkidoudou.mybatis;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * MyBatis-Plus Wrapper 参数适配（无编译期依赖 MP）
 * <p>
 * QueryWrapper / LambdaQueryWrapper / UpdateWrapper 的条件值存放在
 * {@code paramNameValuePairs} 中，BoundSql 占位符属性形如：
 * {@code ew.paramNameValuePairs.MPGENVAL1}。
 * </p>
 * <p>
 * 通过反射调用 {@code getParamNameValuePairs()} 直接读写 Map，避免仅依赖
 * MetaObject 深层路径时在部分 ParamMap / 附加参数场景下漏改或改写失败。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
final class MpWrapperParamSupport {

    /**
     * MP 生成的参数 Map 段名（见 {@code Constants.WRAPPER_PARAM_MIDDLE}）
     */
    static final String PARAM_NAME_VALUE_PAIRS = "paramNameValuePairs";

    private static final String PAIRS_SEGMENT = "." + PARAM_NAME_VALUE_PAIRS + ".";

    private static final String GET_PARAM_NAME_VALUE_PAIRS = "getParamNameValuePairs";

    private MpWrapperParamSupport() {
    }

    /**
     * 是否为 MP Wrapper 动态参数属性路径
     *
     * @param property ParameterMapping#getProperty，例如 {@code ew.paramNameValuePairs.MPGENVAL1}
     */
    static boolean isWrapperParamProperty(String property) {
        return StrUtil.isNotBlank(property) && property.contains(PAIRS_SEGMENT);
    }

    /**
     * 解析 {@code [alias.]paramNameValuePairs.key} 结构
     *
     * @return 解析结果；无法识别时返回 null
     */
    static ParsedProperty parse(String property) {
        if (!isWrapperParamProperty(property)) {
            return null;
        }
        int idx = property.indexOf(PAIRS_SEGMENT);
        if (idx < 0) {
            return null;
        }
        String wrapperPath = idx == 0 ? "" : property.substring(0, idx);
        String pairKey = property.substring(idx + PAIRS_SEGMENT.length());
        if (StrUtil.isBlank(pairKey) || pairKey.indexOf('.') >= 0) {
            return null;
        }
        return new ParsedProperty(wrapperPath, pairKey);
    }

    /**
     * 读取 Wrapper.paramNameValuePairs 中的值
     *
     * @param parameter  MyBatis 参数对象（通常含 {@code ew}）
     * @param property   完整属性路径
     * @return 参数值；找不到时返回 null
     */
    static Object read(Object parameter, String property) {
        ParsedProperty parsed = parse(property);
        if (parsed == null) {
            return null;
        }
        Map<String, Object> pairs = resolveParamNameValuePairs(parameter, parsed.wrapperPath);
        if (pairs == null) {
            return null;
        }
        return pairs.get(parsed.pairKey);
    }

    /**
     * 写入 Wrapper.paramNameValuePairs
     *
     * @return true 表示写入成功
     */
    static boolean write(Object parameter, String property, Object value) {
        ParsedProperty parsed = parse(property);
        if (parsed == null) {
            return false;
        }
        Map<String, Object> pairs = resolveParamNameValuePairs(parameter, parsed.wrapperPath);
        if (pairs == null) {
            return false;
        }
        try {
            pairs.put(parsed.pairKey, value);
            return true;
        } catch (UnsupportedOperationException e) {
            log.debug("paramNameValuePairs is not writable [property={}]: {}", property, e.getMessage());
            return false;
        }
    }

    /**
     * 定位 Wrapper 上的 paramNameValuePairs Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveParamNameValuePairs(Object parameter, String wrapperPath) {
        Object wrapper = resolveWrapper(parameter, wrapperPath);
        if (wrapper == null) {
            return null;
        }
        try {
            Method method = wrapper.getClass().getMethod(GET_PARAM_NAME_VALUE_PAIRS);
            Object pairs = method.invoke(wrapper);
            if (pairs instanceof Map) {
                return (Map<String, Object>) pairs;
            }
        } catch (NoSuchMethodException e) {
            // 非 MP Wrapper，尝试 MetaObject 直接取 Map 属性
            return readPairsViaMetaObject(wrapper);
        } catch (Exception e) {
            log.debug("Failed to invoke getParamNameValuePairs: {}", e.getMessage());
            return readPairsViaMetaObject(wrapper);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readPairsViaMetaObject(Object wrapper) {
        try {
            MetaObject metaObject = SystemMetaObject.forObject(wrapper);
            Object pairs = metaObject.getValue(PARAM_NAME_VALUE_PAIRS);
            if (pairs instanceof Map) {
                return (Map<String, Object>) pairs;
            }
        } catch (Exception e) {
            log.debug("Failed to read paramNameValuePairs via MetaObject: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 按属性前缀拿到 Wrapper 实例；前缀为空时参数对象本身即 Wrapper
     */
    private static Object resolveWrapper(Object parameter, String wrapperPath) {
        if (parameter == null) {
            return null;
        }
        if (StrUtil.isBlank(wrapperPath)) {
            return hasParamNameValuePairs(parameter) ? parameter : null;
        }
        // 优先 Map 直取（ParamMap / HashMap），避免 MetaObject 对 ParamMap 缺 key 抛 BindingException
        if (parameter instanceof Map) {
            Object direct = ((Map<?, ?>) parameter).get(wrapperPath);
            if (direct != null && hasParamNameValuePairs(direct)) {
                return direct;
            }
            // 兼容 wrapperPath 含点（极少见）；再走 MetaObject
        }
        try {
            MetaObject metaObject = SystemMetaObject.forObject(parameter);
            Object wrapper = metaObject.getValue(wrapperPath);
            if (wrapper != null && hasParamNameValuePairs(wrapper)) {
                return wrapper;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve Wrapper [path={}]: {}", wrapperPath, e.getMessage());
            }
        }
        return null;
    }

    private static boolean hasParamNameValuePairs(Object candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            candidate.getClass().getMethod(GET_PARAM_NAME_VALUE_PAIRS);
            return true;
        } catch (NoSuchMethodException e) {
            try {
                MetaObject metaObject = SystemMetaObject.forObject(candidate);
                return metaObject.hasGetter(PARAM_NAME_VALUE_PAIRS);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    static final class ParsedProperty {
        final String wrapperPath;
        final String pairKey;

        ParsedProperty(String wrapperPath, String pairKey) {
            this.wrapperPath = wrapperPath;
            this.pairKey = pairKey;
        }
    }
}
