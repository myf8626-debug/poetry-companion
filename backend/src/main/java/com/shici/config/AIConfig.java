package com.shici.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class AIConfig {
    @ToString.Exclude
    private String apiKey;
    private String modelUrl = "https://api.deepseek.com/chat/completions";
    private String model = "deepseek-flash";
    private Integer maxTokens = 2048;
    private Double temperature = 0.7;
    private boolean thinkingEnabled = false;

    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .readTimeout(java.time.Duration.ofSeconds(60))
                .writeTimeout(java.time.Duration.ofSeconds(60))
                .build();
        objectMapper = new ObjectMapper();
        log.info("DeepSeek配置初始化完成，模型地址: {}", modelUrl);
    }

    public String callTextAI(String systemPrompt, String userPrompt) throws IOException {
        return callChat(List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Shared official DeepSeek client; never log the credential or response body. */
    public String callChat(List<Map<String, String>> messages) throws IOException {
        if (!isConfigured()) {
            throw new IOException("DeepSeek API Key 未配置");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);
        payload.put("thinking", Map.of("type", thinkingEnabled ? "enabled" : "disabled"));
        String requestBody = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url(modelUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("AI调用失败，状态码: {}", response.code());
                throw new IOException("AI调用失败: " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("DeepSeek 返回了空响应");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode content = root == null ? null : root.path("choices").path(0).path("message").path("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                throw new IOException("DeepSeek 未返回有效回答，请稍后重试");
            }
            return content.asText();
        }
    }

    public String callTextAI(String userPrompt) throws IOException {
        String systemPrompt = "你是一位专业的诗词专家，擅长分析诗词的韵律、韵脚、平仄，并提供润色建议。请用中文回复。";
        return callTextAI(systemPrompt, userPrompt);
    }

}
