package com.limiteddrop.qa.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EvalResponse {
    String runId;
    int questionCount;
    double averageKeywordScore;
    double averageCitationScore;
}
