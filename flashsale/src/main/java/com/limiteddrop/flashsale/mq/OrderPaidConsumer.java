package com.limiteddrop.flashsale.mq;

import com.limiteddrop.common.event.OrderPaidEvent;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.flashsale.service.FlashHitLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费 order 的支付成功事件：仅遥测（flash_hit_log 标记 PAID）。库存已预占，无需变动。
 */
@Slf4j
@Component
public class OrderPaidConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.ORDER_PAID, consumerGroup = "flashsale-paid-consumer",
            selectorExpression = Topics.TAG_PAID)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<OrderPaidEvent> {

        private final FlashHitLogWriter hitLogWriter;

        @Override
        public void onMessage(OrderPaidEvent evt) {
            hitLogWriter.markPaid(evt.getOrderNo());
        }
    }
}
