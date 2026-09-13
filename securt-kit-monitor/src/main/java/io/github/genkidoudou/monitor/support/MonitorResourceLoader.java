package io.github.genkidoudou.monitor.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 监控页面静态资源加载器
 *
 * <p>资源根目录固定为 classpath 下的 {@code support/http/resources}，
 * 例如请求路径 {@code /index.html} 会解析到
 * {@code support/http/resources/index.html}。</p>
 *
 * <p>解析过程会拒绝目录穿越（{@code ..}）、反斜杠、URL 编码残留（{@code %}）
 * 与 NUL 字节，确保只能读取资源根目录下的文件。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class MonitorResourceLoader {

    /**
     * 默认资源根目录（classpath 相对路径，不含首尾斜杠）
     */
    public static final String DEFAULT_RESOURCE_ROOT = "support/http/resources";

    private final String resourceRoot;

    public MonitorResourceLoader() {
        this(DEFAULT_RESOURCE_ROOT);
    }

    public MonitorResourceLoader(String resourceRoot) {
        String root = resourceRoot == null ? DEFAULT_RESOURCE_ROOT : resourceRoot;
        while (root.startsWith("/")) {
            root = root.substring(1);
        }
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        this.resourceRoot = root;
    }

    public String getResourceRoot() {
        return resourceRoot;
    }

    /**
     * 加载资源
     *
     * @param pathInfo 相对于监控根路径的路径，如 {@code /index.html}、{@code /css/style.css}
     * @return 资源内容，路径非法或资源不存在时返回 {@code null}
     */
    public Resource load(String pathInfo) {
        String normalized = normalize(pathInfo);
        if (normalized == null) {
            return null;
        }

        String classpathLocation = resourceRoot + normalized;
        InputStream input = openClasspathStream(classpathLocation);
        if (input == null) {
            return null;
        }

        try {
            byte[] content = readAll(input);
            return new Resource(normalized, content, contentTypeOf(normalized));
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(input);
        }
    }

    /**
     * 打开资源输入流（调用方负责关闭）
     *
     * @param pathInfo 相对于监控根路径的路径
     * @return 输入流，路径非法或资源不存在时返回 {@code null}
     */
    public InputStream openStream(String pathInfo) {
        String normalized = normalize(pathInfo);
        if (normalized == null) {
            return null;
        }
        return openClasspathStream(resourceRoot + normalized);
    }

    /**
     * 路径是否指向已知类型的静态资源
     */
    public boolean isResourcePath(String pathInfo) {
        String normalized = normalize(pathInfo);
        return normalized != null && normalized.lastIndexOf('.') > normalized.lastIndexOf('/');
    }

    /**
     * 规范化请求路径，非法路径返回 {@code null}
     */
    String normalize(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }

        String path = pathInfo;

        // 去掉可能残留的查询串 / 锚点
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }

        // 拒绝 NUL 字节、反斜杠与未解码的百分号（可能是穿越编码，如 %2e%2e）
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0 || path.indexOf('%') >= 0) {
            return null;
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 折叠重复斜杠
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }

        // 目录路径不作为资源处理
        if (path.endsWith("/")) {
            return null;
        }

        // 拒绝目录穿越与当前目录片段
        for (String segment : path.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return null;
            }
        }

        return path;
    }

    /**
     * 根据扩展名推断 Content-Type
     */
    public static String contentTypeOf(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".json") || lower.endsWith(".map")) {
            return "application/json;charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".woff")) {
            return "font/woff";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain;charset=UTF-8";
        }
        return "application/octet-stream";
    }

    private InputStream openClasspathStream(String location) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            InputStream input = contextLoader.getResourceAsStream(location);
            if (input != null) {
                return input;
            }
        }
        return MonitorResourceLoader.class.getClassLoader().getResourceAsStream(location);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignore) {
            // 忽略
        }
    }

    /**
     * 已加载的静态资源
     */
    public static class Resource {
        private final String path;
        private final byte[] content;
        private final String contentType;

        public Resource(String path, byte[] content, String contentType) {
            this.path = path;
            this.content = content;
            this.contentType = contentType;
        }

        public String getPath() {
            return path;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
