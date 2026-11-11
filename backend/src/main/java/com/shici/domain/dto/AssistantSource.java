package com.shici.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantSource {
    private Integer index;
    private String poemId;
    private String title;
    private String author;
    private String dynasty;
    private String docType;
    private Double score;
}
