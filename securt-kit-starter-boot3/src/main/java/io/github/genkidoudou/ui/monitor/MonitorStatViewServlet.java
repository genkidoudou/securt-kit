package io.github.genkidoudou.ui.monitor;

import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.support.MonitorDispatcher;
import io.github.genkidoudou.monitor.support.MonitorExchange;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/**
 * Boot3 监控页 Servlet 适配层（jakarta.servlet）
 *
 * <p>对标 Druid {@code StatViewServlet}：静态资源与 JSON API 统一由本 Servlet 入口承接，
 * 业务路由委托给共享模块 {@link MonitorDispatcher}。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class MonitorStatViewServlet extends HttpServlet {

    public static final String SESSION_KEY = "monitor_logged_in";

    private final MonitorDispatcher dispatcher;
    private final MonitorProperties properties;

    public MonitorStatViewServlet(MonitorDispatcher dispatcher, MonitorProperties properties) {
        this.dispatcher = dispatcher;
        this.properties = properties != null ? properties : new MonitorProperties();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        MonitorExchange exchange = new MonitorExchange();
        exchange.setMethod(req.getMethod());
        exchange.setPathInfo(resolvePathInfo(req));
        exchange.setLoggedIn(isLoggedIn(req.getSession(false)));
        copyParams(req, exchange);
        if ("POST".equalsIgnoreCase(req.getMethod()) || "PUT".equalsIgnoreCase(req.getMethod())) {
            exchange.setBody(readBody(req));
        }

        dispatcher.dispatch(exchange);
        writeResponse(req, resp, exchange);
    }

    private String resolvePathInfo(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath() == null ? "" : req.getContextPath();
        String monitorPath = properties.getPath() == null ? "/monitor" : properties.getPath();
        if (!monitorPath.startsWith("/")) {
            monitorPath = "/" + monitorPath;
        }
        String prefix = contextPath + monitorPath;
        if (uri == null) {
            return "/";
        }
        if (uri.startsWith(prefix)) {
            String pathInfo = uri.substring(prefix.length());
            return (pathInfo == null || pathInfo.isEmpty()) ? "/" : pathInfo;
        }
        String pathInfo = req.getPathInfo();
        return (pathInfo == null || pathInfo.isEmpty()) ? "/" : pathInfo;
    }

    private void copyParams(HttpServletRequest req, MonitorExchange exchange) {
        Enumeration<String> names = req.getParameterNames();
        if (names == null) {
            return;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            exchange.setParam(name, req.getParameter(name));
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        if (reader == null) {
            return null;
        }
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute(SESSION_KEY) != null;
    }

    private void writeResponse(HttpServletRequest req, HttpServletResponse resp, MonitorExchange exchange)
            throws IOException {
        Boolean sessionFlag = exchange.getSessionLoggedIn();
        if (sessionFlag != null) {
            if (Boolean.TRUE.equals(sessionFlag)) {
                req.getSession(true).setAttribute(SESSION_KEY, Boolean.TRUE);
            } else {
                HttpSession session = req.getSession(false);
                if (session != null) {
                    session.removeAttribute(SESSION_KEY);
                }
            }
        }

        addSecurityHeaders(resp);

        if (exchange.isRedirect()) {
            resp.sendRedirect(exchange.getRedirectLocation());
            return;
        }

        resp.setStatus(exchange.getStatusCode());
        if (exchange.getContentType() != null) {
            resp.setContentType(exchange.getContentType());
        }
        byte[] body = exchange.getBodyBytes();
        if (body != null && body.length > 0) {
            resp.setContentLength(body.length);
            OutputStream out = resp.getOutputStream();
            out.write(body);
            out.flush();
        }
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
