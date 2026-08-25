package com.limiteddrop.qa.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.limiteddrop.qa.bm25.Bm25Encoder;
import com.limiteddrop.qa.config.QaProperties;
import com.limiteddrop.qa.model.RetrievedChunk;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.utility.request.FlushReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

@Slf4j
@Component
public class MilvusKnowledgeRepository {
    private static final Gson GSON = new Gson();
    private final QaProperties properties;
    private final Bm25Encoder bm25;
    private final MilvusClientV2 client;
    private volatile boolean ready;
    private long lastFlushAt;

    public MilvusKnowledgeRepository(QaProperties properties, Bm25Encoder bm25) {
        this.properties = properties;
        this.bm25 = bm25;
        this.client = new MilvusClientV2(ConnectConfig.builder().uri(properties.getMilvus().getUri()).build());
    }

    public synchronized void ensureReady() {
        if (ready) return;
        String collection = properties.getMilvus().getCollection();
        boolean exists = Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder().collectionName(collection).build()));
        if (!exists) {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .fieldSchemaList(List.of(
                            CreateCollectionReq.FieldSchema.builder().name("id").dataType(DataType.Int64)
                                    .isPrimaryKey(true).autoID(false).build(),
                            CreateCollectionReq.FieldSchema.builder().name("product_id").dataType(DataType.Int64).build(),
                            CreateCollectionReq.FieldSchema.builder().name("source_type").dataType(DataType.VarChar)
                                    .maxLength(20).build(),
                            CreateCollectionReq.FieldSchema.builder().name("source_id").dataType(DataType.VarChar)
                                    .maxLength(64).build(),
                            CreateCollectionReq.FieldSchema.builder().name("chunk_text").dataType(DataType.VarChar)
                                    .maxLength(4096).build(),
                            CreateCollectionReq.FieldSchema.builder().name("dense").dataType(DataType.FloatVector)
                                    .dimension(properties.getMilvus().getDimension()).build(),
                            CreateCollectionReq.FieldSchema.builder().name("sparse").dataType(DataType.SparseFloatVector)
                                    .build()))
                    .build();
            client.createCollection(CreateCollectionReq.builder().collectionName(collection).collectionSchema(schema).build());
            client.createIndex(CreateIndexReq.builder().collectionName(collection)
                    .indexParams(List.of(
                            IndexParam.builder().fieldName("dense").indexType(IndexParam.IndexType.HNSW)
                                    .metricType(IndexParam.MetricType.COSINE)
                                    .extraParams(Map.of("M", 8, "efConstruction", 64)).build(),
                            IndexParam.builder().fieldName("sparse").indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                    .metricType(IndexParam.MetricType.IP).build()))
                    .build());
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
        ready = true;
    }

    public void insert(long milvusId, long productId, String sourceType, String sourceId,
                       String content, List<Float> denseVector, SortedMap<Long, Float> sparseVector) {
        ensureReady();
        JsonObject row = new JsonObject();
        row.addProperty("id", milvusId);
        row.addProperty("product_id", productId);
        row.addProperty("source_type", sourceType);
        row.addProperty("source_id", sourceId);
        row.addProperty("chunk_text", content);
        row.add("dense", GSON.toJsonTree(denseVector));
        row.add("sparse", GSON.toJsonTree(sparseVector));
        client.insert(InsertReq.builder().collectionName(properties.getMilvus().getCollection())
                .data(List.of(row)).build());
    }

    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        ensureReady();
        client.delete(DeleteReq.builder().collectionName(properties.getMilvus().getCollection())
                .ids(new ArrayList<>(ids)).build());
    }

    public List<RetrievedChunk> hybridSearch(String query, List<Float> denseVector, int topK) {
        ensureReady();
        SortedMap<Long, Float> sparseVector = bm25.encode(query);
        if (sparseVector.isEmpty()) return List.of();
        SearchResp response = client.hybridSearch(HybridSearchReq.builder()
                .collectionName(properties.getMilvus().getCollection())
                .searchRequests(List.of(
                        AnnSearchReq.builder().vectorFieldName("dense")
                                .vectors(List.of(new FloatVec(denseVector))).metricType(IndexParam.MetricType.COSINE)
                                .topK(topK).build(),
                        AnnSearchReq.builder().vectorFieldName("sparse")
                                .vectors(List.of(new SparseFloatVec(sparseVector))).metricType(IndexParam.MetricType.IP)
                                .topK(topK).build()))
                .ranker(new WeightedRanker(List.of(0.6f, 0.4f)))
                .topK(topK)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .outFields(List.of("chunk_text", "product_id", "source_type", "source_id"))
                .build());
        List<RetrievedChunk> result = new ArrayList<>();
        for (List<SearchResp.SearchResult> perQuery : response.getSearchResults()) {
            for (SearchResp.SearchResult item : perQuery) {
                Map<String, Object> entity = item.getEntity();
                result.add(RetrievedChunk.builder()
                        .chunkId(asLong(item.getId()))
                        .productId(asLong(entity == null ? null : entity.get("product_id")))
                        .sourceType(asString(entity == null ? null : entity.get("source_type")))
                        .sourceId(asString(entity == null ? null : entity.get("source_id")))
                        .content(asString(entity == null ? null : entity.get("chunk_text")))
                        .score(item.getScore() == null ? 0 : item.getScore()).build());
            }
        }
        return result;
    }

    public void flush() {
        flush(false);
    }

    public synchronized void flush(boolean force) {
        ensureReady();
        long now = System.currentTimeMillis();
        long elapsed = now - lastFlushAt;
        if (!force && elapsed < 10_000L) return;
        if (force && elapsed > 0 && elapsed < 10_000L) {
            try {
                Thread.sleep(10_000L - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        client.flush(FlushReq.builder().collectionNames(List.of(properties.getMilvus().getCollection()))
                .waitFlushedTimeoutMs(30_000L).build());
        lastFlushAt = System.currentTimeMillis();
    }

    public void dropCollectionForTests() {
        if (Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(properties.getMilvus().getCollection()).build()))) {
            client.dropCollection(DropCollectionReq.builder().collectionName(properties.getMilvus().getCollection()).build());
        }
        ready = false;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private static String asString(Object value) { return value == null ? null : value.toString(); }
}
