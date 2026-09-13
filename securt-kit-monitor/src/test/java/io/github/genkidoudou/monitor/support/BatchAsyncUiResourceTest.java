package io.github.genkidoudou.monitor.support;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAsyncUiResourceTest {

    @Test
    void batchUiExposesAsyncControlsAndProgress() throws Exception {
        String html = read("support/http/resources/index.html");
        String js = read("support/http/resources/js/app.js");
        assertTrue(html.contains("batchStartJobBtn"));
        assertTrue(html.contains("batchPauseBtn"));
        assertTrue(html.contains("batchResumeBtn"));
        assertTrue(html.contains("batchCancelBtn"));
        assertTrue(html.contains("batchProgressPanel"));
        assertTrue(js.contains("totalEstimated"));
        assertTrue(js.contains("/batch/jobs.json"));
        assertTrue(js.contains("batchJobs"));
        assertTrue(js.contains("startJobsForSelected"));
        assertTrue(js.contains("controlActiveJob"));
    }

    private static String read(String path) throws Exception {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("missing resource " + path);
        }
        try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }
}
