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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DeepSeekChatClient {
    private final QaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekChatClient(QaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getRag().getConnectTimeoutMs())).build();
    }

    public String answer(String question, List<RetrievedChunk> chunks) {
        String key = properties.getRag().getDeepseek().getApiKey();
        if (key == null || key.isBlank()) {
            return fallback(chunks);
        }
        try {
            String context = formatContext(chunks);
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getRag().getDeepseek().getChatModel());
            body.put("temperature", 0.2);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是限量高端消费品客服。只依据给定资料回答，资料不足时明确说明。引用资料使用 [1]、[2] 格式。"),
                    Map.of("role", "user", "content", "资料：\n" + context + "\n\n问题：" + question)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(properties.getRag().getRequestTimeoutMs()))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("chat HTTP " + response.statusCode());
            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) return content.asText();
        } catch (Exception e) {
            log.warn("DeepSeek unavailable, using extractive fallback: {}", e.getMessage());
        }
        return fallback(chunks);
    }

    private String fallback(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) return "知识库中暂无可用资料，暂时无法回答。";
        StringBuilder answer = new StringBuilder("根据知识库资料：");
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            answer.append(" ").append(chunks.get(i).getContent()).append("[").append(i + 1).append("]");
        }
        return answer.toString();
    }

    private String formatContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            context.append("[").append(i + 1).append("] ")
                    .append(chunks.get(i).getContent()).append("\n");
        }
        return context.toString();
    }

    private String baseUrl() {
        String value = properties.getRag().getDeepseek().getBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
