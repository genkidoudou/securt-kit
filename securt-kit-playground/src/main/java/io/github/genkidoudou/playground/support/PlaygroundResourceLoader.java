package io.github.genkidoudou.playground.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Playground 静态资源加载器（classpath: support/playground/http/resources）。
 *
 * <p>路径刻意与 monitor 的 {@code support/http/resources} 隔离，避免同一 ClassLoader
 * 下两个 JAR 同名资源互相抢占（否则 /playground 会误出 monitor 页面）。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public class PlaygroundResourceLoader {

    public static final String DEFAULT_RESOURCE_ROOT = "support/playground/http/resources";

    private final String resourceRoot;

    public PlaygroundResourceLoader() {
        this(DEFAULT_RESOURCE_ROOT);
    }

    public PlaygroundResourceLoader(String resourceRoot) {
        String root = resourceRoot == null ? DEFAULT_RESOURCE_ROOT : resourceRoot;
        while (root.startsWith("/")) {
            root = root.substring(1);
        }
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        this.resourceRoot = root;
    }

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

    public boolean isResourcePath(String pathInfo) {
        String normalized = normalize(pathInfo);
        return normalized != null && normalized.lastIndexOf('.') > normalized.lastIndexOf('/');
    }

    String normalize(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }
        String path = pathInfo;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0 || path.indexOf('%') >= 0) {
            return null;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.endsWith("/")) {
            return null;
        }
        for (String segment : path.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return null;
            }
        }
        return path;
    }

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
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
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
        return PlaygroundResourceLoader.class.getClassLoader().getResourceAsStream(location);
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
            // ignore
        }
    }

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
