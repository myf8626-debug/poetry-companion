package com.shici.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GameCompanionChatRequest {
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "单条消息最多 2000 字")
    private String message;

    @Size(max = 20, message = "最多携带最近 20 条历史消息")
    private List<@NotNull @Valid Message> history;

    @Data
    public static class Message {
        @NotBlank
        @Pattern(regexp = "user|assistant", message = "历史消息仅支持 user 或 assistant")
        private String role;

        @NotBlank
        @Size(max = 2000, message = "单条历史消息最多 2000 字")
        private String content;
    }
}
