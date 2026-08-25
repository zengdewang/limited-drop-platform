package com.limiteddrop.qa.mq;

import com.limiteddrop.common.event.ProductDocPublished;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.qa.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = Topics.PRODUCT_DOC, consumerGroup = "qa-product-doc-consumer",
        selectorExpression = Topics.TAG_DOC_PUBLISHED)
public class ProductDocListener implements RocketMQListener<ProductDocPublished> {
    private final KnowledgeService knowledgeService;

    @Override
    public void onMessage(ProductDocPublished event) {
        knowledgeService.indexProduct(event);
    }
}
