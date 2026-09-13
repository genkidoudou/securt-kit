package io.github.genkidoudou.playground.support;

import cn.hutool.json.JSONUtil;
import io.github.genkidoudou.playground.PlaygroundEngine;
import io.github.genkidoudou.playground.PlaygroundProperties;
import io.github.genkidoudou.playground.dto.ApiResponse;
import io.github.genkidoudou.playground.dto.ComplexRunRequest;
import io.github.genkidoudou.playground.dto.CrudRequest;
import io.github.genkidoudou.playground.dto.DigestDemoRequest;
import io.github.genkidoudou.playground.dto.LifecycleRequest;
import io.github.genkidoudou.playground.dto.PersonRequest;
import io.github.genkidoudou.playground.dto.ScenarioRunRequest;
import io.github.genkidoudou.playground.dto.ScenarioSqlRunRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Playground 路由：静态资源 + /api/*
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class PlaygroundDispatcher {

    private final PlaygroundEngine engine;
    private final PlaygroundProperties properties;
    private final PlaygroundResourceLoader resourceLoader;

    public PlaygroundDispatcher(PlaygroundEngine engine, PlaygroundProperties properties) {
        this(engine, properties, new PlaygroundResourceLoader());
    }

    public PlaygroundDispatcher(PlaygroundEngine engine,
                                PlaygroundProperties properties,
                                PlaygroundResourceLoader resourceLoader) {
        this.engine = engine;
        this.properties = properties != null ? properties : new PlaygroundProperties();
        this.resourceLoader = resourceLoader != null ? resourceLoader : new PlaygroundResourceLoader();
    }

    public void dispatch(PlaygroundExchange ex) {
        String path = normalizePath(ex.getPathInfo());
        ex.setPathInfo(path);

        if ("/".equals(path) || path.isEmpty()) {
            serveResource(ex, "/index.html");
            return;
        }

        if (path.startsWith("/api/")) {
            handleApi(ex, path);
            return;
        }

        if ("/login.html".equals(path) || path.startsWith("/css/") || path.startsWith("/js/")
                || resourceLoader.isResourcePath(path)) {
            serveResource(ex, path);
            return;
        }

        writeJson(ex, 404, ApiResponse.error(404, "资源不存在: " + path));
    }

    private void handleApi(PlaygroundExchange ex, String path) {
        if ("/api/check.json".equals(path)) {
            Map<String, Object> data = new HashMap<>();
            data.put("loggedIn", isAuthorized(ex));
            data.put("authEnabled", properties.isAuthEnabled());
            writeJson(ex, 200, ApiResponse.success(data));
            return;
        }

        if ("/api/login.json".equals(path)) {
            handleLogin(ex);
            return;
        }

        if ("/api/logout.json".equals(path)) {
            ex.setSessionLoggedIn(Boolean.FALSE);
            writeJson(ex, 200, ApiResponse.success(null, "已退出"));
            return;
        }

        if (properties.isAuthEnabled() && !ex.isLoggedIn()) {
            writeJson(ex, 401, ApiResponse.error(401, "未登录"));
            return;
        }

        try {
            switch (path) {
                case "/api/person/preflight.json":
                    if (!ex.isMethod("GET")) {
                        throw new IllegalArgumentException("接口仅支持 GET");
                    }
                    writeApi(ex, engine.personPreflight(ex.getParam("datasourceId")));
                    break;
                case "/api/person/list.json":
                    writeApi(ex, engine.personList(requirePost(ex, PersonRequest.class)));
                    break;
                case "/api/person/create.json":
                    writeApi(ex, engine.personCreate(requirePost(ex, PersonRequest.class)));
                    break;
                case "/api/person/update.json":
                    writeApi(ex, engine.personUpdate(requirePost(ex, PersonRequest.class)));
                    break;
                case "/api/person/delete.json":
                    writeApi(ex, engine.personDelete(requirePost(ex, PersonRequest.class)));
                    break;
                case "/api/lifecycle/preflight.json":
                    if (!ex.isMethod("GET")) {
                        throw new IllegalArgumentException("接口仅支持 GET");
                    }
                    writeApi(ex, engine.lifecyclePreflight(ex.getParam("datasourceId")));
                    break;
                case "/api/lifecycle/insert.json":
                    writeApi(ex, engine.lifecycleInsert(requirePost(ex, LifecycleRequest.class)));
                    break;
                case "/api/lifecycle/query.json":
                    writeApi(ex, engine.lifecycleQuery(requirePost(ex, LifecycleRequest.class)));
                    break;
                case "/api/lifecycle/update.json":
                    writeApi(ex, engine.lifecycleUpdate(requirePost(ex, LifecycleRequest.class)));
                    break;
                case "/api/lifecycle/verify.json":
                    writeApi(ex, engine.lifecycleVerify(requirePost(ex, LifecycleRequest.class)));
                    break;
                case "/api/lifecycle/tamper.json":
                    writeApi(ex, engine.lifecycleTamper(requirePost(ex, LifecycleRequest.class)));
                    break;
                case "/api/meta.json":
                    writeJson(ex, 200, engine.meta());
                    break;
                case "/api/datasources.json":
                    writeJson(ex, 200, engine.datasources());
                    break;
                case "/api/crud/insert.json":
                    writeJson(ex, 200, engine.insert(requirePost(ex, CrudRequest.class)));
                    break;
                case "/api/crud/update.json":
                    writeJson(ex, 200, engine.update(requirePost(ex, CrudRequest.class)));
                    break;
                case "/api/crud/query.json":
                    writeJson(ex, 200, engine.query(requirePost(ex, CrudRequest.class)));
                    break;
                case "/api/crud/raw-query.json":
                    writeJson(ex, 200, engine.rawQuery(requirePost(ex, CrudRequest.class)));
                    break;
                case "/api/digest/demo.json":
                    writeJson(ex, 200, engine.digestDemo(requirePost(ex, DigestDemoRequest.class)));
                    break;
                case "/api/complex/run.json":
                    writeJson(ex, 200, engine.complexRun(requirePost(ex, ComplexRunRequest.class)));
                    break;
                case "/api/scenarios.json":
                    if (!ex.isMethod("GET")) {
                        throw new IllegalArgumentException("接口仅支持 GET");
                    }
                    writeApi(ex, engine.listScenarios());
                    break;
                case "/api/scenarios/run.json":
                    writeApi(ex, engine.runScenario(requirePost(ex, ScenarioRunRequest.class)));
                    break;
                case "/api/scenarios/seed-preview.json":
                    if (!ex.isMethod("GET")) {
                        throw new IllegalArgumentException("接口仅支持 GET");
                    }
                    writeApi(ex, engine.seedPreview(ex.getParam("datasourceId")));
                    break;
                case "/api/scenarios/sql-run.json":
                    writeApi(ex, engine.sqlRun(requirePost(ex, ScenarioSqlRunRequest.class)));
                    break;
                default:
                    writeJson(ex, 404, ApiResponse.error(404, "接口不存在: " + path));
                    break;
            }
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400, ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("处理 playground 接口 {} 失败", path, e);
            writeJson(ex, 500, ApiResponse.error("请求处理失败: " + e.getMessage()));
        }
    }

    private void handleLogin(PlaygroundExchange ex) {
        if (!ex.isMethod("POST")) {
            writeJson(ex, 405, ApiResponse.error(405, "接口仅支持 POST"));
            return;
        }
        Map<?, ?> body = parseBody(ex.getBody(), Map.class);
        String username = body == null ? null : String.valueOf(body.get("username"));
        String password = body == null ? null : String.valueOf(body.get("password"));
        ApiResponse<?> response = engine.login(username, password);
        if (response.isSuccess()) {
            ex.setSessionLoggedIn(Boolean.TRUE);
            ex.setLoggedIn(true);
        }
        writeJson(ex, response.isSuccess() ? 200 : 401, response);
    }

    private boolean isAuthorized(PlaygroundExchange ex) {
        return !properties.isAuthEnabled() || ex.isLoggedIn();
    }

    private <T> T requirePost(PlaygroundExchange ex, Class<T> type) {
        if (!ex.isMethod("POST")) {
            throw new IllegalArgumentException("接口仅支持 POST");
        }
        return parseBody(ex.getBody(), type);
    }

    private <T> T parseBody(String body, Class<T> type) {
        if (body == null || body.trim().isEmpty()) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("请求体为空");
            }
        }
        try {
            return JSONUtil.toBean(body, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体格式错误，需要合法的 JSON");
        }
    }

    private void serveResource(PlaygroundExchange ex, String path) {
        PlaygroundResourceLoader.Resource resource = resourceLoader.load(path);
        if (resource == null) {
            writeJson(ex, 404, ApiResponse.error(404, "静态资源不存在: " + path));
            return;
        }
        ex.setStatusCode(200);
        ex.setContentType(resource.getContentType());
        ex.setBodyBytes(resource.getContent());
    }

    private void writeJson(PlaygroundExchange ex, int status, Object payload) {
        ex.setStatusCode(status);
        ex.setContentType("application/json;charset=UTF-8");
        ex.setBodyString(JSONUtil.toJsonStr(payload));
    }

    private void writeApi(PlaygroundExchange ex, ApiResponse<?> response) {
        int status = response.getCode() == null ? 200 : response.getCode();
        writeJson(ex, status, response);
    }

    private String normalizePath(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return "/";
        }
        String path = pathInfo;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
