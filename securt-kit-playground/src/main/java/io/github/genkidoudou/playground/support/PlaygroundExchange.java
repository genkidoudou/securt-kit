package io.github.genkidoudou.playground.support;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Playground 请求 / 响应载体（无 Servlet 依赖）
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class PlaygroundExchange {

    public static final String CHARSET = "UTF-8";

    private String method = "GET";
    private String pathInfo;
    private final Map<String, String> params = new LinkedHashMap<>();
    private String body;
    private boolean loggedIn;

    private int statusCode = 200;
    private String contentType;
    private byte[] bodyBytes;
    private String redirectLocation;
    private Boolean sessionLoggedIn;

    public PlaygroundExchange() {
    }

    public PlaygroundExchange(String method, String pathInfo) {
        this.method = method;
        this.pathInfo = pathInfo;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

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

    public void setBodyString(String content) {
        if (content == null) {
            this.bodyBytes = null;
            return;
        }
        try {
            this.bodyBytes = content.getBytes(CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

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

    public void setSessionLoggedIn(Boolean sessionLoggedIn) {
        this.sessionLoggedIn = sessionLoggedIn;
    }

    public boolean isRedirect() {
        return redirectLocation != null;
    }
}
