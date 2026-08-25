package com.limiteddrop.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class QaProperties {

    private String opsKey;
    private Milvus milvus = new Milvus();
    private Rag rag = new Rag();
    private ProductDb productDb = new ProductDb();

    @Data
    public static class Milvus {
        private String uri = "http://localhost:19530";
        private String collection = "drop_kb";
        private int dimension = 1024;
        private int topK = 8;
    }

    @Data
    public static class Rag {
        private Siliconflow siliconflow = new Siliconflow();
        private Deepseek deepseek = new Deepseek();
        private int chunkSize = 300;
        private int chunkOverlap = 30;
        private int denseWeight = 60;
        private int sparseWeight = 40;
        private int rerankTopK = 5;
        private int connectTimeoutMs = 3000;
        private int requestTimeoutMs = 20000;
    }

    @Data
    public static class Siliconflow {
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String apiKey;
        private String embedModel = "BAAI/bge-m3";
        private String rerankModel = "BAAI/bge-reranker-v2-m3";
    }

    @Data
    public static class Deepseek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String chatModel = "deepseek-chat";
    }

    @Data
    public static class ProductDb {
        private String url;
        private String username;
        private String password;
    }
}
