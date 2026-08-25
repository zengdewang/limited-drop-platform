package com.limiteddrop.qa.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.common.event.ProductDocPublished;
import com.limiteddrop.common.event.ReviewModerated;
import com.limiteddrop.qa.bm25.Bm25Encoder;
import com.limiteddrop.qa.config.QaProperties;
import com.limiteddrop.qa.mapper.DocumentChunkMapper;
import com.limiteddrop.qa.milvus.MilvusKnowledgeRepository;
import com.limiteddrop.qa.model.DocumentChunk;
import com.limiteddrop.qa.provider.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@DependsOn("qaSchemaInitializer")
public class KnowledgeService {
    private static final String INDEXED = "INDEXED";
    private static final String FAILED = "FAILED";

    private final DocumentChunkMapper chunkMapper;
    private final MilvusKnowledgeRepository repository;
    private final EmbeddingProvider embeddingProvider;
    private final Bm25Encoder bm25;
    private final QaProperties properties;

    public KnowledgeService(DocumentChunkMapper chunkMapper, MilvusKnowledgeRepository repository,
                            EmbeddingProvider embeddingProvider, Bm25Encoder bm25, QaProperties properties) {
        this.chunkMapper = chunkMapper;
        this.repository = repository;
        this.embeddingProvider = embeddingProvider;
        this.bm25 = bm25;
        this.properties = properties;
    }

    @PostConstruct
    public void warmBm25Statistics() {
        chunkMapper.selectList(Wrappers.emptyWrapper()).stream()
                .filter(chunk -> chunk.getContent() != null)
                .forEach(chunk -> bm25.observeDocument(chunk.getContent()));
    }

    @Transactional
    public void indexProduct(ProductDocPublished event) {
        replace("PRODUCT_DOC", String.valueOf(event.getProductId()), event.getProductId(),
                event.getBrand() + " " + event.getName() + " " + event.getCategory() + "\n" + event.getOfficialDoc());
    }

    @Transactional
    public void indexReview(ReviewModerated event) {
        replace("REVIEW", String.valueOf(event.getReviewId()), event.getProductId(),
                "【用户评价·" + event.getRating() + "星】" + event.getContent());
    }

    @Transactional
    public void removeReview(Long reviewId, Long productId) {
        remove("REVIEW", String.valueOf(reviewId));
    }

    @Transactional
    public void replace(String sourceType, String sourceId, Long productId, String content) {
        remove(sourceType, sourceId);
        List<String> parts = split(content);
        for (int i = 0; i < parts.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setSourceType(sourceType);
            chunk.setSourceId(sourceId);
            chunk.setProductId(productId);
            chunk.setChunkIndex(i);
            chunk.setContent(parts.get(i));
            chunk.setStatus(FAILED);
            chunkMapper.insert(chunk);
            try {
                bm25.observeDocument(chunk.getContent());
                repository.insert(chunk.getId(), productId, sourceType, sourceId, chunk.getContent(),
                        embeddingProvider.embed(chunk.getContent()), bm25.encode(chunk.getContent()));
                chunk.setMilvusId(chunk.getId());
                chunk.setStatus(INDEXED);
            } catch (Exception e) {
                log.error("index chunk failed source={}/{}", sourceType, sourceId, e);
            }
            chunkMapper.updateById(chunk);
        }
        if (!parts.isEmpty()) {
            try {
                repository.flush();
            } catch (Exception e) {
                log.warn("Milvus flush deferred source={}/{}: {}", sourceType, sourceId, e.getMessage());
            }
        }
    }

    public List<DocumentChunk> listChunks() {
        return chunkMapper.selectList(Wrappers.emptyWrapper());
    }

    private void remove(String sourceType, String sourceId) {
        List<DocumentChunk> old = chunkMapper.selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getSourceType, sourceType).eq(DocumentChunk::getSourceId, sourceId));
        old.stream().map(DocumentChunk::getContent).filter(content -> content != null)
                .forEach(bm25::removeDocument);
        List<Long> ids = old.stream().map(DocumentChunk::getMilvusId).filter(id -> id != null).toList();
        try {
            repository.delete(ids);
        } catch (Exception e) {
            log.warn("Milvus delete failed source={}/{}: {}", sourceType, sourceId, e.getMessage());
        }
        if (!old.isEmpty()) {
            chunkMapper.deleteBatchIds(old.stream().map(DocumentChunk::getId).toList());
        }
    }

    private List<String> split(String content) {
        if (content == null || content.isBlank()) return List.of();
        int size = Math.max(80, properties.getRag().getChunkSize());
        int overlap = Math.min(size / 2, Math.max(0, properties.getRag().getChunkOverlap()));
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < content.length();) {
            int end = Math.min(content.length(), start + size);
            String part = content.substring(start, end).trim();
            if (!part.isBlank()) parts.add(part);
            if (end >= content.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return parts;
    }
}
