package io.github.hexlodev.ui.monitor.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.util.regex.Pattern;

/**
 * 安全输入验证注解
 * 用于验证用户输入，防止 XSS 和注入攻击
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeInput.SafeInputValidator.class)
@Documented
public @interface SafeInput {

    /**
     * 最大长度
     */
    int maxLength() default 1000;

    /**
     * 是否允许 HTML 标签
     */
    boolean allowHtml() default false;

    /**
     * 验证模式（正则表达式）
     */
    String pattern() default "";

    /**
     * 错误消息
     */
    String message() default "输入包含不安全字符或格式不正确";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 验证器实现
     */
    class SafeInputValidator implements ConstraintValidator<SafeInput, String> {
        private int maxLength;
        private boolean allowHtml;
        private Pattern pattern;

        @Override
        public void initialize(SafeInput annotation) {
            this.maxLength = annotation.maxLength();
            this.allowHtml = annotation.allowHtml();
            String patternStr = annotation.pattern();
            if (patternStr != null && !patternStr.isEmpty()) {
                this.pattern = Pattern.compile(patternStr);
            }
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true; // 使用 @NotNull 处理空值
            }

            // 1. 长度验证
            if (value.length() > maxLength) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "输入长度不能超过 " + maxLength + " 字符"
                ).addConstraintViolation();
                return false;
            }

        // 2. HTML 标签检查
        if (!allowHtml && containsHtmlTags(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "输入不能包含 HTML 标签或 JavaScript 代码"
            ).addConstraintViolation();
            return false;
        }

            // 3. 模式验证
            if (pattern != null && !pattern.matcher(value).matches()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "输入格式不正确"
                ).addConstraintViolation();
                return false;
            }

            return true;
        }

        /**
         * 检查是否包含 HTML 标签
         */
        private boolean containsHtmlTags(String value) {
            // 检查常见的 HTML 标签和 JavaScript 事件
            Pattern htmlPattern = Pattern.compile(
                "<[^>]*>|javascript:|on\\w+\\s*=",
                Pattern.CASE_INSENSITIVE
            );
            return htmlPattern.matcher(value).find();
        }
    }
}

