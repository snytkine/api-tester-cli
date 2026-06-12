/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.ReportOptions;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders a {@link TestRunResult} together with its originating {@link TestSuite} into a
 * self-contained single-page HTML report and writes it to a file.
 *
 * <p>The report embeds all CSS inline and uses native {@code <details>}/{@code <summary>}
 * expand-collapse. Rendering behaviour is controlled by a {@link ReportOptions} instance passed to
 * {@link #generate}: when {@link ReportOptions#jsEnabled()} is {@code true} a small inline
 * JavaScript formatter is embedded and JSON bodies are stored as compact JSON; when {@code false}
 * JSON bodies are pretty-printed server-side and no script tag is emitted.
 *
 * <p>Thread-safety: this class is a stateless Spring singleton. The static {@link TemplateEngine}
 * is thread-safe after initialization (Thymeleaf's documented guarantee). The {@code ObjectMapper}
 * field is configured once at construction and never mutated afterwards — Jackson's {@code
 * ObjectMapper} is thread-safe for reading/writing when no reconfiguration occurs after
 * construction. All per-invocation state (context, computed strings, file I/O) lives on the call
 * stack.
 */
@Service
public class HtmlReportGenerator {

    private static final TemplateEngine HTML_ENGINE;

    /** Matches a complete {@code <pre>…</pre>} block including its content. */
    private static final Pattern PRE_PATTERN = Pattern.compile("(?s)<pre[^>]*>.*?</pre>");

    /** Matches a complete {@code <style>…</style>} block, capturing the inner content. */
    private static final Pattern STYLE_PATTERN = Pattern.compile("(?s)(<style[^>]*>)(.*?)(</style>)");

    static {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        HTML_ENGINE = engine;
    }

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Renders the supplied test-run result and suite metadata into an HTML report and writes it to
     * {@code outputPath}, using the supplied {@link ReportOptions} to control JS and minification.
     *
     * <p>Any missing parent directories of {@code outputPath} are created automatically. The file is
     * overwritten if it already exists.
     *
     * @param result the aggregated outcome of the test run
     * @param suite the test suite that was executed
     * @param outputPath the file path where the HTML report will be written
     * @param options rendering options controlling JS formatter and HTML minification
     * @throws IOException if template rendering or file I/O fails
     */
    public void generate(TestRunResult result, TestSuite suite, Path outputPath, ReportOptions options)
            throws IOException {
        Context ctx = new Context();
        ctx.setVariable("suiteName", suite.name());
        ctx.setVariable("suiteDescription", suite.description());
        ctx.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        ctx.setVariable("passedCount", result.passedCount());
        ctx.setVariable("failedCount", result.failedCount());
        ctx.setVariable("skippedCount", result.skippedCount());
        ctx.setVariable("errorCount", result.errorCount());
        ctx.setVariable(
                "totalCount",
                result.passedCount() + result.failedCount() + result.skippedCount() + result.errorCount());
        ctx.setVariable(
                "tests",
                result.results().stream()
                        .map(tc -> toTestMap(tc, options.jsEnabled()))
                        .toList());
        ctx.setVariable("jsEnabled", options.jsEnabled());

        String html = HTML_ENGINE.process("suite-report", ctx);
        if (options.minifyEnabled()) {
            html = minify(html);
        }
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        Files.writeString(outputPath, html);
    }

    /**
     * Converts a {@link TestCaseResult} record into a {@code Map<String,Object>} suitable for
     * consumption by the Thymeleaf OGNL template engine.
     *
     * <p>All fields that are nullable in the record are carried into the map as-is; the template uses
     * {@code th:if} guards before accessing them.
     *
     * @param tc the test-case result to convert
     * @param compactJson when {@code true} JSON response bodies are serialised as compact JSON;
     *     when {@code false} they are pretty-printed
     * @return a map with keys matching the template variable names
     */
    private Map<String, Object> toTestMap(TestCaseResult tc, boolean compactJson) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tc.name());
        map.put("result", tc.result().name());
        map.put("statusClass", tc.result().name().toLowerCase());
        map.put("passedAssertions", tc.passedAssertions());
        map.put("failedAssertions", tc.failures().size());
        map.put("skipReason", tc.skipReason());
        map.put("hasRequest", tc.requestInfo() != null);
        map.put(
                "requestMethod",
                tc.requestInfo() != null ? tc.requestInfo().method().name() : null);
        map.put("requestUrl", tc.requestInfo() != null ? tc.requestInfo().url() : null);
        map.put(
                "requestBody",
                tc.requestInfo() != null ? formatJsonString(tc.requestInfo().body(), compactJson) : null);
        map.put(
                "requestHeaders",
                headersToList(tc.requestInfo() != null ? tc.requestInfo().headers() : null));
        map.put("hasResponse", tc.apiResponse() != null);
        map.put("responseStatus", tc.apiResponse() != null ? tc.apiResponse().statusCode() : null);
        map.put("responseTimeMs", tc.apiResponse() != null ? tc.apiResponse().responseTimeMs() : null);
        map.put(
                "responseHeaders",
                headersToList(tc.apiResponse() != null ? tc.apiResponse().headers() : null));
        map.put("formattedResponseBody", formatBody(tc.apiResponse(), compactJson));
        map.put("failures", failuresToList(tc.failures()));
        return map;
    }

    /**
     * Converts a nullable header map into a list of {@code {name, value}} maps for OGNL-safe
     * iteration in the template.
     *
     * @param headers the header map, or {@code null}
     * @return a list of single-entry maps; empty when {@code headers} is {@code null} or empty
     */
    private List<Map<String, String>> headersToList(@Nullable Map<String, String> headers) {
        if (headers == null) {
            return List.of();
        }
        return headers.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    /**
     * Converts a list of {@link AssertionFailure} records into a list of {@code Map<String,Object>}
     * for OGNL-safe iteration in the template.
     *
     * @param failures the list of failures; must not be {@code null} but may be empty
     * @return a list of maps, one per failure, with keys {@code description}, {@code expected},
     *     {@code actual}, and {@code error}
     */
    private List<Map<String, Object>> failuresToList(List<AssertionFailure> failures) {
        return failures.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("description", f.description());
                    m.put("expected", f.expected());
                    m.put("actual", f.actual());
                    m.put("error", f.error());
                    return m;
                })
                .toList();
    }

    /**
     * Formats the response body for display in the report.
     *
     * <p>When {@code compact} is {@code true} and the body contains parsed JSON, the JSON is
     * serialised as a single-line compact string (the browser-side JS formatter will expand it).
     * When {@code compact} is {@code false} the JSON is pretty-printed server-side. Returns the raw
     * body text when the body is not JSON, and {@code null} when the response or its body is absent.
     *
     * @param resp the API response, or {@code null}
     * @param compact when {@code true} serialise JSON compactly; when {@code false} pretty-print
     * @return formatted body string, or {@code null}
     */
    @Nullable private String formatBody(@Nullable ApiResponse resp, boolean compact) {
        if (resp == null || resp.body() == null) {
            return null;
        }
        if (resp.body().json() != null) {
            try {
                return compact
                        ? jsonMapper.writeValueAsString(resp.body().json())
                        : jsonMapper
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(resp.body().json());
            } catch (IOException e) {
                return resp.body().text();
            }
        }
        return resp.body().text();
    }

    /**
     * Attempts to parse {@code text} as JSON and re-serialise it compactly or with pretty-printing.
     *
     * <p>If {@code text} is not valid JSON (e.g. a plain-text request body), it is returned
     * unchanged. Returns {@code null} when {@code text} is {@code null}.
     *
     * @param text raw body text, or {@code null}
     * @param compact when {@code true} serialise as compact JSON; when {@code false} pretty-print
     * @return reformatted JSON string, original text if not JSON, or {@code null}
     */
    @Nullable private String formatJsonString(@Nullable String text, boolean compact) {
        if (text == null) {
            return null;
        }
        try {
            Object parsed = jsonMapper.readValue(text, Object.class);
            return compact
                    ? jsonMapper.writeValueAsString(parsed)
                    : jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (IOException e) {
            return text;
        }
    }

    /**
     * Minifies an HTML string produced by the Thymeleaf template engine.
     *
     * <p>Steps applied in order:
     *
     * <ol>
     *   <li>Extract {@code <pre>…</pre>} blocks and replace them with sentinels so their whitespace
     *       is never touched.
     *   <li>Strip HTML comments ({@code <!-- … -->}).
     *   <li>Collapse all whitespace inside {@code <style>} blocks to single spaces.
     *   <li>Remove whitespace-only text nodes between tags ({@code >\s+<} → {@code ><}).
     *   <li>Remove leading horizontal whitespace from every line.
     *   <li>Restore {@code <pre>} blocks from sentinels.
     *   <li>Run a second inter-tag whitespace pass to clean up boundaries around {@code <pre>}
     *       blocks exposed by restoration.
     * </ol>
     *
     * <p>Thread-safety: stateless static method; safe for concurrent use.
     *
     * @param html the raw HTML produced by Thymeleaf
     * @return minified HTML string
     */
    private static String minify(String html) {
        // Step 1: stash <pre> blocks using null-char sentinels ( PRE{n} ) so steps 4-5
        // cannot corrupt them —   is not space/tab and cannot appear in Thymeleaf output.
        List<String> preBlocks = new ArrayList<>();
        Matcher preMatcher = PRE_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (preMatcher.find()) {
            preBlocks.add(preMatcher.group());
            preMatcher.appendReplacement(sb, " PRE" + (preBlocks.size() - 1) + " ");
        }
        preMatcher.appendTail(sb);
        html = sb.toString();

        // Step 2: strip HTML comments
        html = html.replaceAll("(?s)<!--.*?-->", "");

        // Step 3: collapse whitespace inside <style> blocks
        html = STYLE_PATTERN
                .matcher(html)
                .replaceAll(mr -> Matcher.quoteReplacement(
                        mr.group(1) + mr.group(2).replaceAll("\\s+", " ").strip() + mr.group(3)));

        // Step 4: remove whitespace-only text nodes between tags
        html = html.replaceAll(">\\s+<", "><");

        // Step 5: drop leading whitespace on every line
        html = html.replaceAll("(?m)^[ \\t]+", "");

        // Step 6: restore <pre> blocks
        for (int i = 0; i < preBlocks.size(); i++) {
            html = html.replace(" PRE" + i + " ", preBlocks.get(i));
        }

        // Step 7: clean up inter-tag whitespace exposed at <pre> block boundaries after restoration
        html = html.replaceAll(">\\s+<", "><");

        return html.strip();
    }
}
