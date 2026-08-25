package com.limiteddrop.qa.service;

import com.limiteddrop.common.event.ProductDocPublished;
import com.limiteddrop.common.event.ReviewModerated;
import com.limiteddrop.qa.bm25.Bm25Encoder;
import com.limiteddrop.qa.mapper.DocumentChunkMapper;
import com.limiteddrop.qa.milvus.MilvusKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProductDbReindexService {
    private final JdbcTemplate productJdbcTemplate;
    private final KnowledgeService knowledgeService;
    private final DocumentChunkMapper chunkMapper;
    private final MilvusKnowledgeRepository milvus;
    private final Bm25Encoder bm25;

    public ProductDbReindexService(@Qualifier("productJdbcTemplate") JdbcTemplate productJdbcTemplate,
                                   KnowledgeService knowledgeService, DocumentChunkMapper chunkMapper,
                                   MilvusKnowledgeRepository milvus, Bm25Encoder bm25) {
        this.productJdbcTemplate = productJdbcTemplate;
        this.knowledgeService = knowledgeService;
        this.chunkMapper = chunkMapper;
        this.milvus = milvus;
        this.bm25 = bm25;
    }

    public synchronized int rebuild() {
        milvus.dropCollectionForTests();
        chunkMapper.delete(Wrappers.emptyWrapper());
        bm25.reset();
        List<Map<String, Object>> products = productJdbcTemplate.queryForList(
                "select id, brand, name, category, official_doc from product");
        for (Map<String, Object> row : products) {
            knowledgeService.indexProduct(ProductDocPublished.builder()
                    .productId(number(row.get("id")))
                    .brand(text(row.get("brand"))).name(text(row.get("name")))
                    .category(text(row.get("category"))).officialDoc(text(row.get("official_doc"))).build());
        }
        List<Map<String, Object>> reviews = productJdbcTemplate.queryForList(
                "select id, product_id, rating, content from review where status = 'APPROVED'");
        for (Map<String, Object> row : reviews) {
            knowledgeService.indexReview(ReviewModerated.builder()
                    .reviewId(number(row.get("id"))).productId(number(row.get("product_id")))
                    .rating(row.get("rating") == null ? null : ((Number) row.get("rating")).intValue())
                    .content(text(row.get("content"))).build());
        }
        milvus.flush(true);
        return products.size() + reviews.size();
    }

    private static Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
