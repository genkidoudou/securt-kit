package io.github.genkidoudou.core.parser.constant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * 字段相关常量
 * 
 * @author chu7
 * @date 2025/5/26 11:28
 */
public class FieldConstant {
    
    /**
     * 占位符前缀
     */
    public static final String PLACEHOLDER = "SECURT_KIT_PLACEHOLDER_";
    
    /**
     * 基础数据类型对应的包装类
     */
    public static final Set<Class> FUNDAMENTAL = new HashSet<>();
    
    static {
        FUNDAMENTAL.add(String.class);
        FUNDAMENTAL.add(Integer.class);
        FUNDAMENTAL.add(Long.class);
        FUNDAMENTAL.add(Double.class);
        FUNDAMENTAL.add(Float.class);
        FUNDAMENTAL.add(Boolean.class);
        FUNDAMENTAL.add(Byte.class);
        FUNDAMENTAL.add(Short.class);
        FUNDAMENTAL.add(Character.class);
        FUNDAMENTAL.add(BigDecimal.class);
        FUNDAMENTAL.add(Date.class);
        FUNDAMENTAL.add(LocalDate.class);
        FUNDAMENTAL.add(LocalTime.class);
        FUNDAMENTAL.add(LocalDateTime.class);
    }
}