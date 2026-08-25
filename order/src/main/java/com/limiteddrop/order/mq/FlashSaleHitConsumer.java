package com.limiteddrop.order.mq;

import com.limiteddrop.common.event.FlashSaleHitEvent;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费抢购命中事件 → 幂等建单（ADR-0003 异步削峰）。
 */
@Slf4j
@Component
public class FlashSaleHitConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.FLASH_SALE_HIT, consumerGroup = "order-hit-consumer",
            selectorExpression = Topics.TAG_HIT)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<FlashSaleHitEvent> {

        private final OrderService orderService;

        @Override
        public void onMessage(FlashSaleHitEvent evt) {
            orderService.createFromHit(evt);
        }
    }
}
