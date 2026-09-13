package io.github.genkidoudou.monitor.support;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控请求 / 响应载体（Druid 风格）
 *
 * <p>不依赖任何 Servlet API：由各 Boot 版本的 Servlet 适配层填充请求侧字段，
 * 交给 {@link MonitorDispatcher} 处理，再把响应侧字段写回 HTTP 响应。</p>
 *
 * <p>典型的适配层写回顺序：</p>
 * <ol>
 *   <li>{@link #getSessionLoggedIn()} 非 null 时写入 / 清除会话登录标记</li>
 *   <li>{@link #getRedirectLocation()} 非 null 时执行重定向并结束</li>
 *   <li>否则写出 {@link #getStatusCode()}、{@link #getContentType()}、{@link #getBodyBytes()}</li>
 * </ol>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class MonitorExchange {

    /**
     * 默认字符集
     */
    public static final String CHARSET = "UTF-8";

    // ---------------- 请求侧 ----------------

    /**
     * HTTP 方法，如 {@code GET} / {@code POST}
     */
    private String method = "GET";

    /**
     * 相对于监控根路径的路径，如 {@code /api/encrypt.json}、{@code /index.html}
     */
    private String pathInfo;

    /**
     * 查询串 / 表单参数
     */
    private final Map<String, String> params = new LinkedHashMap<>();

    /**
     * 请求体（JSON 文本）
     */
    private String body;

    /**
     * 当前会话是否已登录
     */
    private boolean loggedIn;

    // ---------------- 响应侧 ----------------

    private int statusCode = 200;

    private String contentType;

    private byte[] bodyBytes;

    /**
     * 重定向地址，非 null 时适配层应执行重定向
     */
    private String redirectLocation;

    /**
     * 会话登录标记变更：{@code null}=不变更，{@code true}=登录，{@code false}=登出
     */
    private Boolean sessionLoggedIn;

    public MonitorExchange() {
    }

    public MonitorExchange(String method, String pathInfo) {
        this.method = method;
        this.pathInfo = pathInfo;
    }

    // ---------------- 请求侧访问器 ----------------

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 请求方法是否匹配（忽略大小写）
     */
    public boolean isMethod(String expected) {
        return expected != null && expected.equalsIgnoreCase(method);
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public void setParam(String name, String value) {
        if (name != null) {
            params.put(name, value);
        }
    }

    public void setParams(Map<String, String> values) {
        if (values != null) {
            params.putAll(values);
        }
    }

    public String getParam(String name) {
        return params.get(name);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    // ---------------- 响应侧访问器 ----------------

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getBodyBytes() {
        return bodyBytes;
    }

    public void setBodyBytes(byte[] bodyBytes) {
        this.bodyBytes = bodyBytes;
    }

    /**
     * 以 UTF-8 写入响应体
     */
    public void setBodyString(String content) {
        if (content == null) {
            this.bodyBytes = null;
            return;
        }
        try {
            this.bodyBytes = content.getBytes(CHARSET);
        } catch (UnsupportedEncodingException e) {
            // UTF-8 一定存在
            throw new IllegalStateException(e);
        }
    }

    /**
     * 以 UTF-8 读取响应体
     */
    public String getBodyString() {
        if (bodyBytes == null) {
            return null;
        }
        try {
            return new String(bodyBytes, CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public void setRedirectLocation(String redirectLocation) {
        this.redirectLocation = redirectLocation;
    }

    public Boolean getSessionLoggedIn() {
        return sessionLoggedIn;
    }

    /**
     * 设置会话登录标记变更
     *
     * @param sessionLoggedIn {@code null}=不变更，{@code true}=登录，{@code false}=登出
     */
    public void setSessionLoggedIn(Boolean sessionLoggedIn) {
        this.sessionLoggedIn = sessionLoggedIn;
    }

    /**
     * 是否为重定向响应
     */
    public boolean isRedirect() {
        return redirectLocation != null;
    }
}
