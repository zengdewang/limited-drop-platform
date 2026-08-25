package com.limiteddrop.product.mq;

import com.limiteddrop.common.event.OrderPaidEvent;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.product.entity.PaidOrder;
import com.limiteddrop.product.mapper.PaidOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费 order 的支付成功事件 → 本地记录 paid_order，供评价资格校验（事件驱动，ADR-0003）。
 */
@Slf4j
@Component
public class OrderPaidConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.ORDER_PAID, consumerGroup = "product-order-paid-consumer",
            selectorExpression = Topics.TAG_PAID)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<OrderPaidEvent> {

        private final PaidOrderMapper paidOrderMapper;

        @Override
        public void onMessage(OrderPaidEvent evt) {
            PaidOrder po = new PaidOrder();
            po.setOrderNo(evt.getOrderNo());
            po.setCustomerId(evt.getCustomerId());
            po.setProductId(evt.getProductId());
            po.setPaidAt(LocalDateTime.now());
            try {
                paidOrderMapper.insert(po);
            } catch (DuplicateKeyException e) {
                // 幂等：uk_order_no 已存在，重试/重复消息无害
            }
        }
    }
}
