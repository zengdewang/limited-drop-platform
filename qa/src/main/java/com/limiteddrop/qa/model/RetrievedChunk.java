package com.limiteddrop.qa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {
    private Long chunkId;
    private Long productId;
    private String sourceType;
    private String sourceId;
    private String content;
    private float score;
    private float rerankScore;
}
