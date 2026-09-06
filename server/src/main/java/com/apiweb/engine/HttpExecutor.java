package com.apiweb.engine;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 执行器：基于 java.net.http.HttpClient，执行单次请求并返回结构化响应。
 */
@Component
public class HttpExecutor {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Response(int status, Map<String, List<String>> headers, String body, long durationMs) {}

    public Response execute(EngineDtos.HttpStep step, VariablesResolver resolver) throws Exception {
        String url = resolver.resolve(step.getUrl());
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("请求 URL 为空");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        // Query 参数拼接
        StringBuilder urlWithQuery = new StringBuilder(url);
        if (step.getQuery() != null && !step.getQuery().isEmpty()) {
            String qs = step.getQuery().stream()
                    .filter(kv -> kv.getEnabled() == null || kv.getEnabled())
                    .map(kv -> kv.getKey() + "=" + java.net.URLEncoder.encode(
                            resolver.resolve(kv.getValue() == null ? "" : kv.getValue()),
                            java.nio.charset.StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            if (!qs.isEmpty()) {
                urlWithQuery.append(url.contains("?") ? "&" : "?").append(qs);
            }
        }

        // Header 组装
        Map<String, String> headers = new LinkedHashMap<>();
        if (step.getHeaders() != null) {
            step.getHeaders().stream()
                    .filter(kv -> kv.getEnabled() == null || kv.getEnabled())
                    .forEach(kv -> headers.put(kv.getKey(), resolver.resolve(kv.getValue())));
        }

        String method = step.getMethod() == null ? "GET" : step.getMethod().toUpperCase();
        String body = resolver.resolve(step.getBody());
        boolean hasBody = body != null && !body.isBlank()
                && !List.of("GET", "HEAD", "DELETE").contains(method);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(urlWithQuery.toString()))
                .timeout(Duration.ofSeconds(60))
                .GET();
        if (hasBody) {
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithQuery.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if (!method.equals("GET")) {
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithQuery.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .method(method, HttpRequest.BodyPublishers.noBody());
        }
        headers.forEach(builder::header);
        if (hasBody && !headers.containsKey("Content-Type")) {
            builder.header("Content-Type", "application/json");
        }

        long start = System.currentTimeMillis();
        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        long duration = System.currentTimeMillis() - start;
        return new Response(response.statusCode(), response.headers().map(),
                response.body(), duration);
    }

    /**
     * 把响应头 Map 转小写键（统一比较）。
     */
    public static Map<String, List<String>> lowercaseHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue,
                        (a, b) -> a));
    }
}
