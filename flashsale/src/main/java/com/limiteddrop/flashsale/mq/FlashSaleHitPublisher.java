package com.limiteddrop.flashsale.mq;

import com.limiteddrop.common.event.FlashSaleHitEvent;
import com.limiteddrop.common.mq.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 抢购命中事件投递：fire-and-forget（异步），绝不在响应路径上等结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleHitPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    public void publishAsync(FlashSaleHitEvent evt) {
        rocketMQTemplate.asyncSend(dest(Topics.FLASH_SALE_HIT, Topics.TAG_HIT), evt, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                // 命中事件投递成功即建单有保障；幂等消费兜底
            }

            @Override
            public void onException(Throwable e) {
                // 投递失败：本地重试由 rocketmq 客户端重试策略兜底；极端丢失可通过对账发现
                log.error("FlashSaleHitEvent 投递失败 orderNo={}", evt.getOrderNo(), e);
            }
        });
    }

    private static String dest(String topic, String tag) {
        return topic + ":" + tag;
    }
}
