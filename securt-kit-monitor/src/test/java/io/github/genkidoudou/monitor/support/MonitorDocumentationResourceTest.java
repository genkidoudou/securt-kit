package io.github.genkidoudou.monitor.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorDocumentationResourceTest {

    private static final List<String> HELP_TOPICS = Arrays.asList(
            "home", "crypto", "rowVerify", "batch", "sql", "config");

    @Test
    void everyMonitorPageHasHelpAndOneSharedDialog() throws Exception {
        String html = resource("/support/http/resources/index.html");

        for (String topic : HELP_TOPICS) {
            assertTrue(html.contains("data-help-topic=\"" + topic + "\""),
                    "缺少帮助入口: " + topic);
            assertTrue(html.contains("id=\"help-template-" + topic + "\""),
                    "缺少帮助模板: " + topic);
        }
        assertTrue(html.contains("id=\"helpDialog\""));
        assertTrue(html.contains("role=\"dialog\""));
        assertTrue(html.contains("aria-modal=\"true\""));
        assertTrue(html.contains("id=\"helpDialogClose\""));
        assertTrue(html.contains("data-tab=\"docs\""));
        assertTrue(html.contains("id=\"docsTab\""));
    }

    @Test
    void projectDocsCoverRequiredTopicsAndCopyTargets() throws Exception {
        String html = resource("/support/http/resources/index.html");

        for (String section : Arrays.asList(
                "docs-overview", "docs-install", "docs-modes", "docs-fields",
                "docs-digest-like", "docs-multi-datasource", "docs-tools", "docs-faq")) {
            assertTrue(html.contains("id=\"" + section + "\""),
                    "缺少项目文档章节: " + section);
            assertTrue(html.contains("href=\"#" + section + "\""),
                    "缺少项目文档导航: " + section);
        }
        assertTrue(html.contains("securt-kit-starter-boot2"));
        assertTrue(html.contains("securt-kit-starter-boot3"));
        assertTrue(html.contains("jdbc:interceptor:"));
        assertTrue(html.contains("mode: MYBATIS"));
        assertTrue(html.contains("data-copy-target="));
        assertTrue(html.contains("aria-live=\"polite\""));
    }

    @Test
    void helpAndCopyControllersExposeRequiredInteractions() throws Exception {
        String javascript = resource("/support/http/resources/js/app.js");

        assertTrue(javascript.contains("const helpCenter ="));
        assertTrue(javascript.contains("open(topic, trigger)"));
        assertTrue(javascript.contains("event.key === 'Escape'"));
        assertTrue(javascript.contains("event.target === overlay"));
        assertTrue(javascript.contains("this.lastTrigger.focus()"));
        assertTrue(javascript.contains("helpCenter.init()"));
        assertTrue(javascript.contains("[data-copy-target]"));
        assertTrue(javascript.contains("utils.copyToClipboard"));
        assertTrue(javascript.contains("复制失败"));
        assertTrue(javascript.contains("已复制"));
    }

    @Test
    void documentationLayoutIsBoundedAndResponsive() throws Exception {
        String css = resource("/support/http/resources/css/style.css");

        assertTrue(css.contains(".section-head"));
        assertTrue(css.contains(".help-modal-body"));
        assertTrue(css.contains(".docs-layout"));
        assertTrue(css.contains(".docs-content"));
        assertTrue(css.contains("overflow: auto"));
        assertTrue(css.contains("@media (max-width: 900px)"));
        assertTrue(css.contains("grid-template-columns: 1fr"));
    }

    private static String resource(String path) throws Exception {
        try (InputStream input = MonitorDocumentationResourceTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing resource: " + path);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
