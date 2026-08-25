package com.limiteddrop.qa.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AskResponse {
    String answer;
    List<SourceResponse> sources;
}
