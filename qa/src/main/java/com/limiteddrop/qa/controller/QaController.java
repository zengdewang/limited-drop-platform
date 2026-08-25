package com.limiteddrop.qa.controller;

import com.limiteddrop.common.api.Result;
import com.limiteddrop.qa.dto.AskRequest;
import com.limiteddrop.qa.dto.AskResponse;
import com.limiteddrop.qa.dto.EvalResponse;
import com.limiteddrop.qa.dto.SourceResponse;
import com.limiteddrop.qa.model.RetrievedChunk;
import com.limiteddrop.qa.service.EvaluationService;
import com.limiteddrop.qa.service.ProductDbReindexService;
import com.limiteddrop.qa.service.RetrievalService;
import com.limiteddrop.qa.provider.DeepSeekChatClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {
    private final RetrievalService retrievalService;
    private final DeepSeekChatClient chatClient;
    private final ProductDbReindexService reindexService;
    private final EvaluationService evaluationService;
    private final QaOpsGuard opsGuard;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }

    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        List<RetrievedChunk> chunks = retrievalService.retrieve(request.getQuestion(), request.getTopK());
        String answer = chatClient.answer(request.getQuestion(), chunks);
        List<SourceResponse> sources = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sources.add(SourceResponse.builder().reference(i + 1).chunkId(chunk.getChunkId())
                    .productId(chunk.getProductId()).sourceType(chunk.getSourceType()).sourceId(chunk.getSourceId())
                    .content(chunk.getContent()).score(chunk.getScore()).rerankScore(chunk.getRerankScore()).build());
        }
        return Result.ok(AskResponse.builder().answer(answer).sources(sources).build());
    }

    @PostMapping("/reindex")
    public Result<Integer> reindex(@RequestHeader("X-Ops-Key") String opsKey) {
        opsGuard.require(opsKey);
        return Result.ok(reindexService.rebuild());
    }

    @PostMapping("/eval/run")
    public Result<EvalResponse> evaluate(@RequestHeader("X-Ops-Key") String opsKey) {
        opsGuard.require(opsKey);
        return Result.ok(evaluationService.run());
    }
}
