package io.github.genkidoudou.monitor.support;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.monitor.MonitorEngine;
import io.github.genkidoudou.monitor.MonitorProperties;
import io.github.genkidoudou.monitor.dto.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控请求路由器（Druid 风格）
 *
 * <p>负责路径路由、登录态拦截、JSON 序列化与静态资源读取，
 * 使各 Boot 版本的 Servlet 适配层只需搬运 {@link MonitorExchange} 的字段。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class MonitorDispatcher {

    /**
     * JSON 响应 Content-Type
     */
    public static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final MonitorEngine engine;

    private final MonitorProperties properties;

    private final MonitorResourceLoader resourceLoader;

    public MonitorDispatcher(MonitorEngine engine, MonitorProperties properties) {
        this(engine, properties, new MonitorResourceLoader());
    }

    public MonitorDispatcher(MonitorEngine engine, MonitorProperties properties,
                             MonitorResourceLoader resourceLoader) {
        this.engine = engine;
        this.properties = properties != null ? properties : new MonitorProperties();
        this.resourceLoader = resourceLoader != null ? resourceLoader : new MonitorResourceLoader();
    }

    /**
     * 处理一次监控请求
     *
     * @param ex 请求 / 响应载体，处理结果写入其响应侧字段
     */
    public void dispatch(MonitorExchange ex) {
        if (ex == null) {
            return;
        }

        String path = normalizePathInfo(ex.getPathInfo());

        // 根路径 -> 重定向到首页
        if ("/".equals(path)) {
            ex.setRedirectLocation(basePath() + "/index.html");
            return;
        }

        // 退出登录
        if ("/logout".equals(path)) {
            ex.setSessionLoggedIn(Boolean.FALSE);
            ex.setRedirectLocation(basePath() + "/");
            return;
        }

        // 登录（表单提交：application/x-www-form-urlencoded）
        if ("/login".equals(path)) {
            handleLogin(ex);
            return;
        }

        // API 接口
        if (path.startsWith("/api/")) {
            handleApi(ex, path);
            return;
        }

        // 静态资源
        handleResource(ex, path);
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    private void handleLogin(MonitorExchange ex) {
        if (!ex.isMethod("POST")) {
            writeJson(ex, 405, ApiResponse.error(405, "登录接口仅支持 POST"));
            return;
        }

        String username = ex.getParam("username");
        String password = ex.getParam("password");

        ApiResponse<?> response = engine.login(username, password);
        if (response.isSuccess()) {
            ex.setSessionLoggedIn(Boolean.TRUE);
            ex.setLoggedIn(true);
        }
        writeJson(ex, 200, response);
    }

    // ------------------------------------------------------------------
    // API 路由
    // ------------------------------------------------------------------

    private void handleApi(MonitorExchange ex, String path) {
        // 登录状态查询接口不需要鉴权
        if ("/api/check.json".equals(path)) {
            Map<String, Object> data = new HashMap<>();
            data.put("loggedIn", ex.isLoggedIn());
            writeJson(ex, 200, ApiResponse.success(data));
            return;
        }

        // 其余接口统一鉴权
        if (!ex.isLoggedIn()) {
            writeJson(ex, 401, ApiResponse.error(401, "未登录"));
            return;
        }

        try {
            switch (path) {
                case "/api/encrypt.json":
                    requirePost(ex, path, EncryptRequest.class);
                    break;
                case "/api/decrypt.json":
                    requirePost(ex, path, DecryptRequest.class);
                    break;
                case "/api/parse-sql.json":
                    requirePost(ex, path, ParseSqlRequest.class);
                    break;
                case "/api/encrypt-sql.json":
                    requirePost(ex, path, EncryptSqlRequest.class);
                    break;
                case "/api/query-sql.json":
                    requirePost(ex, path, QuerySqlRequest.class);
                    break;
                case "/api/data-init/encrypt.json":
                    requirePost(ex, path, DataInitRequest.class);
                    break;
                case "/api/data-init/decrypt.json":
                    requirePost(ex, path, DataInitRequest.class);
                    break;
                case "/api/strategies.json":
                    writeJson(ex, 200, engine.getStrategies());
                    break;
                case "/api/datasources.json":
                    writeJson(ex, 200, engine.getDatasources());
                    break;
                case "/api/config.json":
                    writeJson(ex, 200, engine.getConfig());
                    break;
                case "/api/primary-key.json":
                    writeJson(ex, 200, engine.resolvePrimaryKey(ex.getParam("table"), ex.getParam("idColumn")));
                    break;
                case "/api/digest.json":
                    requirePost(ex, path, DigestToolRequest.class);
                    break;
                case "/api/row/verify.json":
                    requirePost(ex, path, RowVerifyRequest.class);
                    break;
                case "/api/row/load.json":
                    requirePost(ex, path, RowVerifyRequest.class);
                    break;
                case "/api/batch/preview.json":
                    requirePost(ex, path, BatchPreviewRequest.class);
                    break;
                case "/api/batch/jobs.json":
                    requirePost(ex, path, BatchPreviewRequest.class);
                    break;
                case "/api/batch/apply.json":
                    requirePost(ex, path, BatchApplyRequest.class);
                    break;
                default:
                    if (handleBatchJobPath(ex, path)) {
                        break;
                    }
                    writeJson(ex, 404, ApiResponse.error(404, "接口不存在: " + path));
                    break;
            }
        } catch (Exception e) {
            log.error("处理监控接口 {} 失败", path, e);
            writeJson(ex, 500, ApiResponse.error("请求处理失败: " + e.getMessage()));
        }
    }

    /**
     * /api/batch/jobs/{id}.json 与 pause/resume/cancel。
     */
    private boolean handleBatchJobPath(MonitorExchange ex, String path) {
        final String prefix = "/api/batch/jobs/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            return false;
        }
        String rest = path.substring(prefix.length(), path.length() - ".json".length());
        if (rest.isEmpty()) {
            return false;
        }
        String jobId;
        String action = null;
        int slash = rest.indexOf('/');
        if (slash < 0) {
            jobId = rest;
        } else {
            jobId = rest.substring(0, slash);
            action = rest.substring(slash + 1);
        }
        if (jobId.isEmpty()) {
            return false;
        }
        if (action == null) {
            if (!ex.isMethod("GET")) {
                writeJson(ex, 405, ApiResponse.error(405, "作业状态仅支持 GET"));
                return true;
            }
            writeJson(ex, 200, engine.batchJobStatus(jobId));
            return true;
        }
        if (!ex.isMethod("POST")) {
            writeJson(ex, 405, ApiResponse.error(405, "接口仅支持 POST: " + path));
            return true;
        }
        if ("pause".equals(action)) {
            writeJson(ex, 200, engine.batchPause(jobId));
            return true;
        }
        if ("resume".equals(action)) {
            writeJson(ex, 200, engine.batchResume(jobId));
            return true;
        }
        if ("cancel".equals(action)) {
            writeJson(ex, 200, engine.batchCancel(jobId));
            return true;
        }
        return false;
    }

    /**
     * 校验请求方法为 POST，解析 JSON 请求体并调用对应引擎方法
     */
    private <T> void requirePost(MonitorExchange ex, String path, Class<T> requestType) {
        if (!ex.isMethod("POST")) {
            writeJson(ex, 405, ApiResponse.error(405, "接口仅支持 POST: " + path));
            return;
        }

        T request;
        try {
            request = parseBody(ex.getBody(), requestType);
        } catch (Exception e) {
            log.warn("解析请求体失败: {}", e.getMessage());
            writeJson(ex, 400, ApiResponse.error(400, "请求体格式错误，需要合法的 JSON"));
            return;
        }

        writeJson(ex, 200, invoke(path, request));
    }

    private ApiResponse<?> invoke(String path, Object request) {
        switch (path) {
            case "/api/encrypt.json":
                return engine.encrypt((EncryptRequest) request);
            case "/api/decrypt.json":
                return engine.decrypt((DecryptRequest) request);
            case "/api/parse-sql.json":
                return engine.parseSql((ParseSqlRequest) request);
            case "/api/encrypt-sql.json":
                return engine.encryptSql((EncryptSqlRequest) request);
            case "/api/query-sql.json":
                return engine.querySql((QuerySqlRequest) request);
            case "/api/data-init/encrypt.json":
                return engine.dataInitEncrypt((DataInitRequest) request);
            case "/api/data-init/decrypt.json":
                return engine.dataInitDecrypt((DataInitRequest) request);
            case "/api/digest.json":
                return engine.digestTool((DigestToolRequest) request);
            case "/api/row/verify.json":
                return engine.rowVerify((RowVerifyRequest) request);
            case "/api/row/load.json":
                return engine.rowLoad((RowVerifyRequest) request);
            case "/api/batch/preview.json":
                return engine.batchPreview((BatchPreviewRequest) request);
            case "/api/batch/jobs.json":
                return engine.batchCreateJob((BatchPreviewRequest) request);
            case "/api/batch/apply.json":
                return engine.batchApply((BatchApplyRequest) request);
            default:
                return ApiResponse.error(404, "接口不存在: " + path);
        }
    }

    /**
     * 反序列化 JSON 请求体；请求体为空时返回带默认值的空对象
     */
    private <T> T parseBody(String body, Class<T> requestType) {
        String json = body == null || body.trim().isEmpty() ? "{}" : body.trim();
        return JSONUtil.toBean(json, requestType);
    }

    // ------------------------------------------------------------------
    // 静态资源
    // ------------------------------------------------------------------

    private void handleResource(MonitorExchange ex, String path) {
        if (!ex.isMethod("GET") && !ex.isMethod("HEAD")) {
            ex.setStatusCode(405);
            ex.setContentType("text/plain;charset=UTF-8");
            ex.setBodyString("Method Not Allowed");
            return;
        }

        MonitorResourceLoader.Resource resource = resourceLoader.load(path);
        if (resource == null) {
            ex.setStatusCode(404);
            ex.setContentType("text/plain;charset=UTF-8");
            ex.setBodyString("Not Found: " + path);
            return;
        }

        ex.setStatusCode(200);
        ex.setContentType(resource.getContentType());
        ex.setBodyBytes(resource.getContent());
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private void writeJson(MonitorExchange ex, int statusCode, Object payload) {
        ex.setStatusCode(statusCode);
        ex.setContentType(JSON_CONTENT_TYPE);
        ex.setBodyString(JSONUtil.toJsonStr(payload));
    }

    /**
     * 监控根路径，形如 {@code /monitor}（无尾部斜杠）
     */
    private String basePath() {
        String path = properties.getPath();
        if (path == null || path.trim().isEmpty()) {
            return "/monitor";
        }
        path = path.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 规范化相对路径：空路径视为 {@code /}
     */
    private String normalizePathInfo(String pathInfo) {
        if (pathInfo == null || pathInfo.trim().isEmpty()) {
            return "/";
        }
        String path = pathInfo.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
}
