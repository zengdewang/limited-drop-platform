package com.limiteddrop.qa.bm25;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChineseTokenizer {
    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return segmenter.sentenceProcess(text).stream()
                .filter(token -> !token.isBlank())
                .toList();
    }
}
