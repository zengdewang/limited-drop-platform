package com.limiteddrop.qa.mq;

import com.limiteddrop.common.event.ReviewUnmoderated;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.qa.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = Topics.REVIEW_MODERATED, consumerGroup = "qa-review-unmoderated-consumer",
        selectorExpression = Topics.TAG_UNMODERATED)
public class ReviewUnmoderatedListener implements RocketMQListener<ReviewUnmoderated> {
    private final KnowledgeService knowledgeService;

    @Override
    public void onMessage(ReviewUnmoderated event) {
        knowledgeService.removeReview(event.getReviewId(), event.getProductId());
    }
}
