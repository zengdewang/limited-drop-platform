package com.limiteddrop.qa.bm25;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Java-side BM25 encoder. Term ids are stable hashes, so Milvus stores sparse vectors directly. */
@Component
public class Bm25Encoder {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final ChineseTokenizer tokenizer;
    private final Map<Long, Integer> documentFrequency = new HashMap<>();
    private long documentCount;
    private double totalDocumentLength;

    public Bm25Encoder(ChineseTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public synchronized void observeDocument(String text) {
        List<String> tokens = tokenizer.tokenize(text);
        if (tokens.isEmpty()) {
            return;
        }
        documentCount++;
        totalDocumentLength += tokens.size();
        for (Long term : new HashSet<>(tokens.stream().map(this::termId).toList())) {
            documentFrequency.merge(term, 1, Integer::sum);
        }
    }

    public synchronized void removeDocument(String text) {
        List<String> tokens = tokenizer.tokenize(text);
        if (tokens.isEmpty() || documentCount == 0) return;
        documentCount--;
        totalDocumentLength = Math.max(0, totalDocumentLength - tokens.size());
        for (Long term : new HashSet<>(tokens.stream().map(this::termId).toList())) {
            documentFrequency.computeIfPresent(term, (key, value) -> value <= 1 ? null : value - 1);
        }
    }

    public synchronized SortedMap<Long, Float> encode(String text) {
        List<String> tokens = tokenizer.tokenize(text);
        SortedMap<Long, Float> vector = new TreeMap<>();
        if (tokens.isEmpty()) {
            return vector;
        }
        Map<Long, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) {
            termFrequency.merge(termId(token), 1, Integer::sum);
        }
        double avgLength = documentCount == 0 ? tokens.size() : Math.max(1d, totalDocumentLength / documentCount);
        for (Map.Entry<Long, Integer> entry : termFrequency.entrySet()) {
            int df = documentFrequency.getOrDefault(entry.getKey(), 0);
            double idf = Math.log(1d + (documentCount - df + 0.5d) / (df + 0.5d));
            double tf = entry.getValue();
            double denominator = tf + K1 * (1d - B + B * tokens.size() / avgLength);
            vector.put(entry.getKey(), (float) (idf * (tf * (K1 + 1d) / denominator)));
        }
        return vector;
    }

    public List<String> tokens(String text) {
        return tokenizer.tokenize(text);
    }

    public synchronized void reset() {
        documentFrequency.clear();
        documentCount = 0;
        totalDocumentLength = 0;
    }

    private long termId(String token) {
        byte[] bytes = token.toLowerCase().getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= b & 0xffL;
            hash *= 0x100000001b3L;
        }
        // Milvus sparse indices are uint32 (1..2^32-1), not arbitrary longs.
        long id = hash & 0xffffffffL;
        return id == 0 ? 1 : id;
    }
}
