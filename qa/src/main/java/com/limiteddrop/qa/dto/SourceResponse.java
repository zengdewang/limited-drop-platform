package com.limiteddrop.qa.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SourceResponse {
    int reference;
    Long chunkId;
    Long productId;
    String sourceType;
    String sourceId;
    String content;
    float score;
    float rerankScore;
}
