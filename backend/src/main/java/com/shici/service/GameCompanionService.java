package com.shici.service;

import com.shici.config.AIConfig;
import com.shici.domain.dto.AssistantChatResponse;
import com.shici.domain.dto.GameCompanionChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCompanionService {
    private final AIConfig aiConfig;

    private static final String SYSTEM_PROMPT = """
            你是诗词学习平台“诗韵游园”里的古风小书童，负责陪伴用户游玩、讲解玩法和回答诗词问题。
            用自然亲切的中文回答，带少量古风语气，不使用晦涩文言。通常用两三句短句，适合语音朗读。
            引用诗词时保持原文和作者准确，不确定就说明，不能编造出处。
            你可以介绍飞花令、诗词接龙等诗词游戏的一般玩法。
            具体关卡、积分、赛季、排名和通关条件必须以游戏页面为准；未提供的规则不能猜测。
            不声称自己能看到用户的实时游戏画面、分数或账号信息，也不声称已经执行游戏操作。
            """;

    public AssistantChatResponse chat(GameCompanionChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (request.getHistory() != null) {
            for (GameCompanionChatRequest.Message message : request.getHistory()) {
                messages.add(Map.of("role", message.getRole(), "content", message.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", request.getMessage().trim()));
        long started = System.nanoTime();
        try {
            String reply = aiConfig.callChat(messages);
            return AssistantChatResponse.builder()
                    .success(true)
                    .reply(reply)
                    .provider("deepseek")
                    .grounded(false)
                    .sources(List.of())
                    .retrievalMs(0L)
                    .generationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .build();
        } catch (IOException exception) {
            log.warn("小书童 DeepSeek 调用失败，异常类型: {}", exception.getClass().getSimpleName());
            return AssistantChatResponse.builder()
                    .success(false)
                    .provider("deepseek")
                    .grounded(false)
                    .sources(List.of())
                    .errorMsg(aiConfig.isConfigured()
                            ? "小书童暂时无法回答，请稍后重试"
                            : "小书童尚未配置 DeepSeek 服务，请联系管理员")
                    .build();
        }
    }
}
