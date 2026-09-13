package io.github.genkidoudou.monitor.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlWorkspaceResourceTest {

    @Test
    void sqlWorkspaceSeparatesQueryAndModificationWithoutRenamingExistingControls() throws Exception {
        String html = resource("/support/http/resources/index.html");

        assertTrue(html.contains("data-sql-view=\"query\""));
        assertTrue(html.contains("data-sql-view=\"modify\""));
        assertTrue(html.contains("id=\"sqlModifyView\""));
        assertTrue(html.contains("data-sql-mode=\"parse\""));
        assertTrue(html.contains("data-sql-mode=\"encrypt\""));

        assertTrue(html.contains("id=\"sqlQueryInput\""));
        assertTrue(html.contains("id=\"sqlInput\""));
        assertTrue(html.contains("id=\"sqlEncryptInput\""));
        assertTrue(html.contains("id=\"querySqlBtn\""));
        assertTrue(html.contains("id=\"parseSqlBtn\""));
        assertTrue(html.contains("id=\"encryptSqlBtn\""));
    }

    @Test
    void sqlModesKeepIndependentDrafts() throws Exception {
        String javascript = resource("/support/http/resources/js/app.js");

        assertFalse(javascript.contains("nextInput.value = prevInput.value"));
        assertTrue(javascript.contains("switchView(view)"));
        assertTrue(javascript.contains("currentView: 'query'"));
        assertTrue(javascript.contains("currentModifyMode: 'parse'"));
    }

    @Test
    void sqlResultsAreBoundedAndModificationLayoutIsResponsive() throws Exception {
        String css = resource("/support/http/resources/css/style.css");

        assertTrue(css.contains(".sql-workspace-result"));
        assertTrue(css.contains("overflow: auto"));
        assertTrue(css.contains("position: sticky"));
        assertTrue(css.contains("@media (max-width: 900px)"));
    }

    @Test
    void sqlQueryResultSwitchesBetweenPlainAndCipherViews() throws Exception {
        String html = resource("/support/http/resources/index.html");
        String javascript = resource("/support/http/resources/js/app.js");

        assertTrue(html.contains("id=\"sqlQueryViewSwitch\""));
        assertTrue(html.contains("data-query-result-view=\"plain\""));
        assertTrue(html.contains("data-query-result-view=\"cipher\""));
        assertTrue(javascript.contains("currentResultView: 'plain'"));
        assertTrue(javascript.contains("switchResultView(view)"));
        assertTrue(javascript.contains("renderCurrentResult()"));
        assertFalse(javascript.contains(
                "'<h4>明文 / 解密</h4>' + html + '<h4 style=\"margin-top:16px;\">密文</h4>'"));
    }

    @Test
    void emptyParseResultsUseSafeDomElementsInsteadOfEscapedMarkup() throws Exception {
        String javascript = resource("/support/http/resources/js/app.js");

        assertTrue(javascript.contains("setEmptyContent(element, text)"));
        assertTrue(javascript.contains("setEmptyContent(container, '无占位符映射')"));
        assertTrue(javascript.contains("setEmptyContent(container, '无需要加密的字段')"));
        assertFalse(javascript.contains(
                "setHtmlContent(container, '<div class=\"empty-message\">无占位符映射</div>')"));
    }

    private static String resource(String path) throws Exception {
        try (InputStream input = SqlWorkspaceResourceTest.class.getResourceAsStream(path)) {
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
