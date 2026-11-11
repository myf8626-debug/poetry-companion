package com.shici.controller;

import com.shici.common.R;
import com.shici.service.GameCompanionVoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/game/companion/voice")
@RequiredArgsConstructor
public class GameCompanionVoiceController {
    private final GameCompanionVoiceService voice;
    public record SpeechRequest(@NotBlank @Size(max = 2000) String text) {}

    @GetMapping("/status")
    public R<?> status() {
        if (!voice.isConfigured()) {
            return R.fail("语音服务未配置，请在后台所在电脑运行 tools/voice-service/setup.ps1，再重启后台");
        }
        try {
            var health = voice.status();
            return R.ok(Map.of("ready", "ok".equals(health.path("status").asText()),
                    "asrReady", health.path("asr").path("ready").asBoolean(),
                    "asrModel", health.path("asr").path("model").asText(),
                    "voice", health.path("voice").asText()));
        } catch (IOException exception) {
            return R.fail("无法连接语音服务，请在后台所在电脑启动 tools/voice-service/start-local.ps1（默认5050端口）");
        }
    }

    @PostMapping(value = "/transcribe", consumes = "audio/wav")
    public ResponseEntity<?> transcribe(@RequestBody byte[] wav) {
        if (wav.length < 44 || wav.length > 1_000_000) {
            return ResponseEntity.badRequest().body(R.fail("请提交不超过30秒的有效录音"));
        }
        try {
            var result = voice.transcribe(wav);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(Map.of(
                    "text", result.path("text").asText(), "recognitionMs", result.path("recognition_ms").asLong())));
        } catch (IOException exception) {
            return ResponseEntity.status(503).body(R.fail("没有识别成功，请重试或输入文字"));
        }
    }

    @PostMapping("/speech")
    public ResponseEntity<?> speak(@Valid @RequestBody SpeechRequest request) {
        try {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                    .cacheControl(CacheControl.noStore()).body(voice.speak(request.text()));
        } catch (IOException exception) {
            return ResponseEntity.status(503).body(R.fail("朗读暂时不可用，回答已保留在对话中"));
        }
    }
}
