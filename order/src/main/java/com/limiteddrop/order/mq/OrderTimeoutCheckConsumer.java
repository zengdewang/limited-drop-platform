package com.limiteddrop.order.mq;

import com.limiteddrop.common.event.PaymentTimeoutCheck;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费延迟自消息：支付超时检查。
 */
@Slf4j
@Component
public class OrderTimeoutCheckConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.ORDER_TIMEOUT, consumerGroup = "order-timeout-check-consumer",
            selectorExpression = Topics.TAG_CHECK)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<PaymentTimeoutCheck> {

        private final OrderService orderService;

        @Override
        public void onMessage(PaymentTimeoutCheck check) {
            orderService.checkTimeout(check);
        }
    }
}
