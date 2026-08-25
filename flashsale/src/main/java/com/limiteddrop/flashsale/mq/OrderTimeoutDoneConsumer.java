package com.limiteddrop.flashsale.mq;

import com.limiteddrop.common.event.OrderPaymentTimeoutEvent;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.flashsale.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费 order 的支付超时事件 → 释放库存（幂等）。
 */
@Slf4j
@Component
public class OrderTimeoutDoneConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.ORDER_TIMEOUT_DONE, consumerGroup = "flashsale-release-consumer",
            selectorExpression = Topics.TAG_RELEASE)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<OrderPaymentTimeoutEvent> {

        private final FlashSaleService flashSaleService;

        @Override
        public void onMessage(OrderPaymentTimeoutEvent evt) {
            flashSaleService.release(evt.getDropId(), evt.getCustomerId(), evt.getOrderNo());
            log.info("库存释放 dropId={} customerId={} orderNo={}", evt.getDropId(), evt.getCustomerId(), evt.getOrderNo());
        }
    }
}
