package com.limiteddrop.qa.service;

import com.limiteddrop.qa.config.QaProperties;
import com.limiteddrop.qa.milvus.MilvusKnowledgeRepository;
import com.limiteddrop.qa.model.RetrievedChunk;
import com.limiteddrop.qa.provider.EmbeddingProvider;
import com.limiteddrop.qa.provider.RerankerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RetrievalService {
    private final EmbeddingProvider embeddingProvider;
    private final MilvusKnowledgeRepository repository;
    private final RerankerClient rerankerClient;
    private final QaProperties properties;

    public RetrievalService(EmbeddingProvider embeddingProvider, MilvusKnowledgeRepository repository,
                            RerankerClient rerankerClient, QaProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.rerankerClient = rerankerClient;
        this.properties = properties;
    }

    public List<RetrievedChunk> retrieve(String question, Integer topK) {
        int requested = topK == null ? properties.getMilvus().getTopK() : Math.max(1, Math.min(topK, 50));
        try {
            List<RetrievedChunk> candidates = repository.hybridSearch(question, embeddingProvider.embed(question), requested);
            return rerankerClient.rerank(question, candidates, Math.min(properties.getRag().getRerankTopK(), requested));
        } catch (Exception e) {
            log.warn("hybrid retrieval unavailable: {}", e.getMessage());
            return List.of();
        }
    }
}
