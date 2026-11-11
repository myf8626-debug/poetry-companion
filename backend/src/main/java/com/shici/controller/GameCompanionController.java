package com.shici.controller;

import com.shici.common.R;
import com.shici.config.AIConfig;
import com.shici.domain.dto.AssistantChatResponse;
import com.shici.domain.dto.GameCompanionChatRequest;
import com.shici.service.GameCompanionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/game/companion")
@RequiredArgsConstructor
public class GameCompanionController {
    private final GameCompanionService gameCompanionService;
    private final AIConfig aiConfig;

    /** Reports local configuration only; it does not make a billed model request. */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(Map.of("configured", aiConfig.isConfigured(), "model", aiConfig.getModel()));
    }

    @PostMapping("/chat")
    public R<AssistantChatResponse> chat(@Valid @RequestBody GameCompanionChatRequest request) {
        return R.ok(gameCompanionService.chat(request));
    }
}
