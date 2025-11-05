package io.github.hexlodev.ui.monitor.security;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * HTTP 安全头过滤器
 * 添加安全响应头，防止 XSS、点击劫持等攻击
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        // 只对监控页面路径添加安全头
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/monitor")) {
            addSecurityHeaders(response);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 添加安全响应头
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // X-Content-Type-Options: 防止 MIME 类型嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // X-Frame-Options: 防止点击劫持
        response.setHeader("X-Frame-Options", "DENY");

        // X-XSS-Protection: 启用浏览器 XSS 过滤
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer-Policy: 控制 Referer 头的发送
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Content-Security-Policy: 内容安全策略（可选，可能影响功能）
        // response.setHeader("Content-Security-Policy", "default-src 'self'");
    }
}

