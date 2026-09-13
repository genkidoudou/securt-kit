package com.example.playground;

import io.github.genkidoudou.playground.PlaygroundProperties;
import io.github.genkidoudou.playground.support.PlaygroundDispatcher;
import io.github.genkidoudou.playground.support.PlaygroundExchange;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/**
 * 测试工程专用：Playground Servlet（javax）。不进入 starter。
 */
public class PlaygroundStatViewServlet extends HttpServlet {

    public static final String SESSION_KEY = "playground_logged_in";

    private final PlaygroundDispatcher dispatcher;
    private final PlaygroundProperties properties;

    public PlaygroundStatViewServlet(PlaygroundDispatcher dispatcher, PlaygroundProperties properties) {
        this.dispatcher = dispatcher;
        this.properties = properties != null ? properties : new PlaygroundProperties();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PlaygroundExchange exchange = new PlaygroundExchange();
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
        String playgroundPath = properties.getPath() == null ? "/playground" : properties.getPath();
        if (!playgroundPath.startsWith("/")) {
            playgroundPath = "/" + playgroundPath;
        }
        String prefix = contextPath + playgroundPath;
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

    private void copyParams(HttpServletRequest req, PlaygroundExchange exchange) {
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

    private void writeResponse(HttpServletRequest req, HttpServletResponse resp, PlaygroundExchange exchange)
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
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
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
}
