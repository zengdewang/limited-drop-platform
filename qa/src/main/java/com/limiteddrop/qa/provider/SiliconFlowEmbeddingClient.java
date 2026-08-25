package com.limiteddrop.qa.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limiteddrop.qa.config.QaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SiliconFlowEmbeddingClient implements EmbeddingProvider {
    private final QaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SiliconFlowEmbeddingClient(QaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRag().getConnectTimeoutMs()))
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        String key = properties.getRag().getSiliconflow().getApiKey();
        if (key == null || key.isBlank()) {
            return fallback(text, properties.getMilvus().getDimension());
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getRag().getSiliconflow().getEmbedModel());
            body.put("input", List.of(text));
            body.put("encoding_format", "float");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/embeddings"))
                    .timeout(Duration.ofMillis(properties.getRag().getRequestTimeoutMs()))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("embedding HTTP " + response.statusCode());
            }
            JsonNode embedding = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalStateException("embedding response missing data");
            }
            List<Float> result = new ArrayList<>(embedding.size());
            embedding.forEach(node -> result.add((float) node.asDouble()));
            return result;
        } catch (Exception e) {
            log.warn("SiliconFlow embedding unavailable, using deterministic fallback: {}", e.getMessage());
            return fallback(text, properties.getMilvus().getDimension());
        }
    }

    private String baseUrl() {
        String value = properties.getRag().getSiliconflow().getBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    static List<Float> fallback(String text, int dimension) {
        List<Float> result = new ArrayList<>(dimension);
        long seed = text == null ? 0 : text.hashCode();
        double norm = 0;
        for (int i = 0; i < dimension; i++) {
            double value = Math.sin(seed * 0.0001 + i * 0.017) + Math.cos(seed * 0.00007 - i * 0.013);
            result.add((float) value);
            norm += value * value;
        }
        float scale = (float) Math.sqrt(Math.max(norm, 1e-9));
        for (int i = 0; i < dimension; i++) {
            result.set(i, result.get(i) / scale);
        }
        return result;
    }
}
