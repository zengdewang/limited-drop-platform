package com.limiteddrop.flashsale.mq;

import com.limiteddrop.common.event.DropPublished;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.flashsale.entity.DropSession;
import com.limiteddrop.flashsale.mapper.DropSessionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费 product 的 DropPublished → 本地 drop_session（幂等 upsert）。
 */
@Slf4j
@Component
public class DropSessionConsumer {

    @Component
    @RocketMQMessageListener(topic = Topics.DROP_PUBLISHED, consumerGroup = "flashsale-drop-session-consumer",
            selectorExpression = Topics.TAG_DROP_PUBLISHED)
    @RequiredArgsConstructor
    public static class Listener implements RocketMQListener<DropPublished> {

        private final DropSessionMapper dropSessionMapper;

        @Override
        public void onMessage(DropPublished evt) {
            DropSession s = dropSessionMapper.selectOne(
                    Wrappers.<DropSession>lambdaQuery().eq(DropSession::getDropId, evt.getDropId()));
            if (s == null) {
                s = new DropSession();
                s.setDropId(evt.getDropId());
                s.setStatus("SCHEDULED");
            }
            s.setProductId(evt.getProductId());
            s.setName(evt.getName());
            s.setStartTime(evt.getStartTime() == null ? null : LocalDateTime.parse(evt.getStartTime()));
            s.setEndTime(evt.getEndTime() == null ? null : LocalDateTime.parse(evt.getEndTime()));
            s.setStock(evt.getStock());
            s.setPriceCents(evt.getPriceCents());
            if (s.getId() == null) {
                dropSessionMapper.insert(s);
            } else {
                dropSessionMapper.updateById(s);
            }
            log.info("drop_session 同步完成 dropId={} stock={}", evt.getDropId(), evt.getStock());
        }
    }
}
