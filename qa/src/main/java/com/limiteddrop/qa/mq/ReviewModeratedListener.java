package com.limiteddrop.qa.mq;

import com.limiteddrop.common.event.ReviewModerated;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.qa.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = Topics.REVIEW_MODERATED, consumerGroup = "qa-review-moderated-consumer",
        selectorExpression = Topics.TAG_MODERATED)
public class ReviewModeratedListener implements RocketMQListener<ReviewModerated> {
    private final KnowledgeService knowledgeService;

    @Override
    public void onMessage(ReviewModerated event) {
        knowledgeService.indexReview(event);
    }
}
