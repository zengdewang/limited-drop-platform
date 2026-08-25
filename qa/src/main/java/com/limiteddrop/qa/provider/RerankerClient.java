package com.limiteddrop.qa.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limiteddrop.qa.config.QaProperties;
import com.limiteddrop.qa.model.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RerankerClient {
    private final QaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RerankerClient(QaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getRag().getConnectTimeoutMs())).build();
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks, int topK) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        String key = properties.getRag().getSiliconflow().getApiKey();
        if (key != null && !key.isBlank()) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", properties.getRag().getSiliconflow().getRerankModel());
                body.put("query", query);
                body.put("documents", chunks.stream().map(RetrievedChunk::getContent).toList());
                body.put("top_n", Math.min(topK, chunks.size()));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/rerank"))
                        .timeout(Duration.ofMillis(properties.getRag().getRequestTimeoutMs()))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("rerank HTTP " + response.statusCode());
                }
                JsonNode results = objectMapper.readTree(response.body()).path("results");
                List<RetrievedChunk> ranked = new ArrayList<>();
                for (JsonNode item : results) {
                    int index = item.path("index").asInt(-1);
                    if (index >= 0 && index < chunks.size()) {
                        RetrievedChunk original = chunks.get(index);
                        original.setRerankScore((float) item.path("relevance_score").asDouble(original.getScore()));
                        ranked.add(original);
                    }
                }
                if (!ranked.isEmpty()) {
                    return ranked.subList(0, Math.min(topK, ranked.size()));
                }
            } catch (Exception e) {
                log.warn("SiliconFlow rerank unavailable, using lexical fallback: {}", e.getMessage());
            }
        }
        List<String> queryTerms = terms(query);
        chunks.forEach(chunk -> chunk.setRerankScore(lexicalScore(queryTerms, chunk.getContent())));
        return chunks.stream().sorted(Comparator.comparing(RetrievedChunk::getRerankScore).reversed())
                .limit(topK).toList();
    }

    private float lexicalScore(List<String> terms, String content) {
        if (terms.isEmpty() || content == null) return 0;
        long hits = terms.stream().filter(content::contains).count();
        return (float) hits / terms.size();
    }

    private List<String> terms(String query) {
        List<String> result = new ArrayList<>();
        for (String part : query.split("\\s+")) {
            if (!part.isBlank()) result.add(part);
            for (int i = 0; i < part.length(); i++) result.add(String.valueOf(part.charAt(i)));
        }
        return result;
    }

    private String baseUrl() {
        String value = properties.getRag().getSiliconflow().getBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
