package com.shici.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantChatResponse {

    private String reply;

    private boolean success;

    private String errorMsg;

    /** 是否检索到达到阈值的知识库证据。 */
    private Boolean grounded;

    /** 实际使用的生成通道：external、local或none。 */
    private String provider;

    private Long retrievalMs;

    private Long generationMs;

    private List<AssistantSource> sources;
}
