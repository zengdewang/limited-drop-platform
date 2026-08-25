package com.limiteddrop.flashsale.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.event.FlashSaleHitEvent;
import com.limiteddrop.common.redis.FlashSaleKey;
import com.limiteddrop.flashsale.dto.BuyResponse;
import com.limiteddrop.flashsale.dto.InfoResponse;
import com.limiteddrop.flashsale.entity.DropSession;
import com.limiteddrop.flashsale.mapper.DropSessionMapper;
import com.limiteddrop.flashsale.mq.FlashSaleHitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀核心：预热 → Lua 原子扣减 → MQ 异步建单 → 支付超时释放库存。
 * 热路径只碰 Redis，不碰 DB、不阻塞等 MQ。见 ADR-0001。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> buyScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DropSessionMapper dropSessionMapper;
    private final FlashSaleHitPublisher hitPublisher;
    private final FlashHitLogWriter hitLogWriter;

    @Value("${app.flashsale.pay-window-minutes:15}")
    private long payWindowMinutes;

    @Value("${app.flashsale.release-buffer-minutes:10}")
    private long releaseBufferMinutes;

    /** ops：开售预热 */
    @Transactional
    public void open(Long dropId) {
        DropSession s = requireSession(dropId);
        LocalDateTime now = LocalDateTime.now();
        long durationSec = Math.max(60, Duration.between(now, s.getEndTime()).getSeconds());
        // 预热：库存归位、清场、开售标记（TTL=发售时长）
        redis.opsForValue().set(FlashSaleKey.inv(dropId), String.valueOf(s.getStock()));
        redis.delete(FlashSaleKey.users(dropId));
        java.util.Set<String> orderKeys = redis.keys(FlashSaleKey.orderPattern(dropId));
        if (orderKeys != null && !orderKeys.isEmpty()) {
            redis.delete(orderKeys);
        }
        redis.opsForValue().set(FlashSaleKey.open(dropId), "1", Duration.ofSeconds(durationSec));
        s.setStatus("OPEN");
        dropSessionMapper.updateById(s);
        log.info("drop {} 已开售，stock={}, 时长={}s", dropId, s.getStock(), durationSec);
    }

    /** ops：关停 */
    @Transactional
    public void close(Long dropId) {
        redis.delete(FlashSaleKey.open(dropId));
        DropSession s = requireSession(dropId);
        s.setStatus("ENDED");
        dropSessionMapper.updateById(s);
    }

    public InfoResponse info(Long dropId) {
        DropSession s = requireSession(dropId);
        String open = redis.opsForValue().get(FlashSaleKey.open(dropId));
        String inv = redis.opsForValue().get(FlashSaleKey.inv(dropId));
        String status = open != null ? "OPEN" : s.getStatus();
        Long remaining = null;
        if (open != null) {
            remaining = inv == null ? s.getStock().longValue() : Long.parseLong(inv);
        }
        return InfoResponse.builder()
                .dropId(dropId).status(status).remaining(remaining)
                .stock(s.getStock()).startTime(s.getStartTime()).endTime(s.getEndTime())
                .build();
    }

    /** 抢购：单次 Lua 往返，原子扣减 + 幂等 */
    public BuyResponse buy(Long dropId, Long customerId) {
        DropSession s = requireSession(dropId);
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        long payWindowMs = payWindowMinutes * 60_000L;
        long ttlMs = (payWindowMinutes + releaseBufferMinutes) * 60_000L;
        long expireAtMillis = System.currentTimeMillis() + payWindowMs;

        Long code = redis.execute(buyScript,
                List.of(FlashSaleKey.inv(dropId), FlashSaleKey.users(dropId),
                        FlashSaleKey.open(dropId), FlashSaleKey.order(dropId, customerId)),
                String.valueOf(customerId), "1", orderNo, String.valueOf(ttlMs));
        if (code == null) {
            throw ApiException.of(500, "抢购服务异常");
        }
        if (code == -3) {
            return BuyResponse.builder().code(-3).build();
        }
        if (code == -2) {
            String existing = redis.opsForValue().get(FlashSaleKey.order(dropId, customerId));
            return BuyResponse.builder().code(-2).orderNo(existing).build();
        }
        if (code == -1) {
            return BuyResponse.builder().code(-1).build();
        }

        // 命中：异步投递建单事件（绝不在响应路径上阻塞），异步写审计日志
        FlashSaleHitEvent evt = FlashSaleHitEvent.builder()
                .orderNo(orderNo)
                .customerId(customerId)
                .productId(s.getProductId())
                .dropId(dropId)
                .amountCents(s.getPriceCents())
                .expireAtMillis(expireAtMillis)
                .build();
        hitPublisher.publishAsync(evt);
        hitLogWriter.recordReserved(orderNo, customerId, dropId);
        return BuyResponse.builder().code(0).orderNo(orderNo).remaining(code).build();
    }

    /** 释放库存（order 支付超时事件触发）；重复释放幂等 */
    public void release(Long dropId, Long customerId, String orderNo) {
        DropSession s = requireSession(dropId);
        redis.execute(releaseScript,
                List.of(FlashSaleKey.users(dropId), FlashSaleKey.inv(dropId),
                        FlashSaleKey.order(dropId, customerId)),
                String.valueOf(customerId), String.valueOf(s.getStock()));
        hitLogWriter.markReleased(orderNo, customerId, dropId);
    }

    private DropSession requireSession(Long dropId) {
        DropSession s = dropSessionMapper.selectOne(
                Wrappers.<DropSession>lambdaQuery().eq(DropSession::getDropId, dropId));
        if (s == null) {
            throw ApiException.of(404, "发售不存在");
        }
        return s;
    }
}
