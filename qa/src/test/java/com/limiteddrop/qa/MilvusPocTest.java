package com.limiteddrop.qa;

import com.limiteddrop.qa.bm25.Bm25Encoder;
import com.limiteddrop.qa.bm25.ChineseTokenizer;
import com.limiteddrop.qa.config.QaProperties;
import com.limiteddrop.qa.milvus.MilvusKnowledgeRepository;
import com.limiteddrop.qa.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Milvus 2.5.10 POC：Java 端 BM25 稀疏向量 + bge-m3 形状的稠密向量。 */
class MilvusPocTest {
    private static final int DIM = 1024;

    @Test
    void hybridSearchWorksWithJavaBm25() {
        QaProperties properties = new QaProperties();
        properties.getMilvus().setUri("http://localhost:19530");
        properties.getMilvus().setCollection("qa_poc_java_bm25");
        properties.getMilvus().setDimension(DIM);
        Bm25Encoder bm25 = new Bm25Encoder(new ChineseTokenizer());
        MilvusKnowledgeRepository repository = new MilvusKnowledgeRepository(properties, bm25);
        repository.dropCollectionForTests();

        List<Doc> docs = List.of(
                new Doc(1, "【商品官方介绍】经典铂金包采用Togo牛皮，手感柔软，皮质极佳，适合日常通勤。", vec(1)),
                new Doc(2, "【用户评价·4星】尺码偏小，建议买大一号，但皮质手感真的很好。", vec(2)),
                new Doc(3, "【用户评价·5星】物流很快，包装精美，正品无疑，非常满意。", vec(3)));
        for (Doc doc : docs) {
            bm25.observeDocument(doc.text());
            repository.insert(doc.id(), 101, doc.id() == 1 ? "PRODUCT_DOC" : "REVIEW", String.valueOf(doc.id()),
                    doc.text(), doc.vector(), bm25.encode(doc.text()));
        }
        repository.flush();

        List<RetrievedChunk> result = repository.hybridSearch("尺码偏小", vec(1), 3);
        assertFalse(result.isEmpty(), "混合检索应返回结果");
        assertTrue(result.stream().anyMatch(chunk -> chunk.getContent().contains("尺码偏小")),
                "Java BM25 稀疏腿应命中含尺码的评价");
    }

    private static List<Float> vec(int seed) {
        List<Float> v = new ArrayList<>(DIM);
        double sum = 0;
        for (int i = 0; i < DIM; i++) {
            double x = Math.sin(seed * 1000.0 + i * 0.017) + 1.0;
            v.add((float) x);
            sum += x * x;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < DIM; i++) v.set(i, v.get(i) / norm);
        return v;
    }

    private record Doc(long id, String text, List<Float> vector) {}
}
