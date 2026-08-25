package com.limiteddrop.qa.service;

import com.limiteddrop.qa.dto.EvalResponse;
import com.limiteddrop.qa.model.RetrievedChunk;
import com.limiteddrop.qa.provider.DeepSeekChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@DependsOn("qaSchemaInitializer")
public class EvaluationService {
    private final JdbcTemplate jdbcTemplate;
    private final RetrievalService retrievalService;
    private final DeepSeekChatClient chatClient;

    public EvalResponse run() {
        String runId = UUID.randomUUID().toString().replace("-", "");
        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                "select id, question, expected_keywords from eval_question order by id");
        double keywordTotal = 0;
        double citationTotal = 0;
        for (Map<String, Object> row : questions) {
            String question = String.valueOf(row.get("question"));
            List<RetrievedChunk> sources = retrievalService.retrieve(question, null);
            String answer = chatClient.answer(question, sources);
            double keyword = keywordScore(answer, String.valueOf(row.get("expected_keywords")));
            double citation = sources.isEmpty() ? 0 : (answer.matches("(?s).*\\[\\d+].*") ? 1 : 0);
            keywordTotal += keyword;
            citationTotal += citation;
            jdbcTemplate.update("insert into eval_run(run_id, question_id, answer, keyword_score, citation_score, total_score) values (?, ?, ?, ?, ?, ?)",
                    runId, row.get("id"), answer, keyword, citation, (keyword + citation) / 2d);
        }
        int count = questions.size();
        return EvalResponse.builder().runId(runId).questionCount(count)
                .averageKeywordScore(count == 0 ? 0 : keywordTotal / count)
                .averageCitationScore(count == 0 ? 0 : citationTotal / count).build();
    }

    private double keywordScore(String answer, String csv) {
        if (csv == null || csv.isBlank() || "null".equals(csv)) return 1;
        String[] keywords = csv.split(",");
        int matched = 0;
        for (String keyword : keywords) if (!keyword.isBlank() && answer.contains(keyword.trim())) matched++;
        return keywords.length == 0 ? 0 : (double) matched / keywords.length;
    }
}
