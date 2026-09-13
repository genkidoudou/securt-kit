package io.github.test.playground;

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
 * 多数据源测试工程专用 Playground Servlet。
 */
public class PlaygroundServlet extends HttpServlet {

    private static final String SESSION_KEY = "playground_logged_in";
    private final PlaygroundDispatcher dispatcher;
    private final PlaygroundProperties properties;

    public PlaygroundServlet(PlaygroundDispatcher dispatcher, PlaygroundProperties properties) {
        this.dispatcher = dispatcher;
        this.properties = properties != null ? properties : new PlaygroundProperties();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PlaygroundExchange exchange = new PlaygroundExchange();
        exchange.setMethod(request.getMethod());
        exchange.setPathInfo(resolvePath(request));
        exchange.setLoggedIn(isLoggedIn(request.getSession(false)));
        copyParams(request, exchange);
        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
            exchange.setBody(readBody(request));
        }
        dispatcher.dispatch(exchange);
        writeResponse(request, response, exchange);
    }

    private String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = properties.getPath() == null ? "/playground" : properties.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String prefix = context + path;
        if (uri != null && uri.startsWith(prefix)) {
            String relative = uri.substring(prefix.length());
            return relative.isEmpty() ? "/" : relative;
        }
        String relative = request.getPathInfo();
        return relative == null || relative.isEmpty() ? "/" : relative;
    }

    private void copyParams(HttpServletRequest request, PlaygroundExchange exchange) {
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            exchange.setParam(name, request.getParameter(name));
        }
    }

    private String readBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        BufferedReader reader = request.getReader();
        char[] buffer = new char[1024];
        int count;
        while (reader != null && (count = reader.read(buffer)) != -1) {
            body.append(buffer, 0, count);
        }
        return body.length() == 0 ? null : body.toString();
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute(SESSION_KEY) != null;
    }

    private void writeResponse(HttpServletRequest request,
                               HttpServletResponse response,
                               PlaygroundExchange exchange) throws IOException {
        if (exchange.getSessionLoggedIn() != null) {
            if (Boolean.TRUE.equals(exchange.getSessionLoggedIn())) {
                request.getSession(true).setAttribute(SESSION_KEY, Boolean.TRUE);
            } else {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute(SESSION_KEY);
                }
            }
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        if (exchange.isRedirect()) {
            response.sendRedirect(exchange.getRedirectLocation());
            return;
        }
        response.setStatus(exchange.getStatusCode());
        if (exchange.getContentType() != null) {
            response.setContentType(exchange.getContentType());
        }
        byte[] body = exchange.getBodyBytes();
        if (body != null && body.length > 0) {
            response.setContentLength(body.length);
            OutputStream output = response.getOutputStream();
            output.write(body);
            output.flush();
        }
    }
}
