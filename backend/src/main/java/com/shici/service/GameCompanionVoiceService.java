package com.shici.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

/** Proxies a configured local voice service without exposing its credential to browsers. */
@Service
public class GameCompanionVoiceService {
    @Value("${ai.companion.voice.base-url:http://127.0.0.1:5050}")
    private String baseUrl;
    @Value("${ai.companion.voice.api-key:}")
    private String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private OkHttpClient http;

    @PostConstruct
    public void init() {
        http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60)).callTimeout(Duration.ofSeconds(70))
                .followRedirects(false).build();
    }

    private Request.Builder request(String path) throws IOException {
        if (!isConfigured()) throw new IOException("Voice service is not configured");
        return new Request.Builder().url(baseUrl.replaceAll("/+$", "") + path)
                .header("Authorization", "Bearer " + apiKey);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public JsonNode status() throws IOException {
        try (Response response = http.newCall(request("/health").get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Voice health check failed");
            return mapper.readTree(response.body().string());
        }
    }

    public JsonNode transcribe(byte[] wav) throws IOException {
        try (Response response = http.newCall(request("/v1/audio/transcriptions")
                .post(RequestBody.create(wav, MediaType.get("audio/wav"))).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Speech recognition failed: " + response.code());
            return mapper.readTree(response.body().string());
        }
    }

    public byte[] speak(String text) throws IOException {
        // Let the voice server use the user's selected voice, pitch and speed defaults.
        byte[] payload = mapper.writeValueAsBytes(java.util.Map.of("input", text, "model", "tts-1", "response_format", "mp3"));
        try (Response response = http.newCall(request("/v1/audio/speech")
                .post(RequestBody.create(payload, MediaType.get("application/json"))).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Speech synthesis failed: " + response.code());
            if (!response.header("Content-Type", "").startsWith("audio/")) throw new IOException("Invalid speech response");
            byte[] audio = response.body().bytes();
            if (audio.length == 0 || audio.length > 8_000_000) throw new IOException("Invalid speech size");
            return audio;
        }
    }
}
