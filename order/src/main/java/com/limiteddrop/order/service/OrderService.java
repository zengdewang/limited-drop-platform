package com.limiteddrop.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.event.FlashSaleHitEvent;
import com.limiteddrop.common.event.OrderPaidEvent;
import com.limiteddrop.common.event.OrderPaymentTimeoutEvent;
import com.limiteddrop.common.event.PaymentTimeoutCheck;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.order.dto.OrderResponse;
import com.limiteddrop.order.entity.Order;
import com.limiteddrop.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 订单服务：异步建单（幂等）→ 模拟支付 → 支付超时释放库存触发。
 * 释放库存的触发器在这里——它是唯一知道支付状态的服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String EXPIRED = "EXPIRED";

    private final OrderMapper orderMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${app.order.payment-delay-level:15}")
    private int paymentDelayLevel;

    @Value("${app.order.check-retry-delay-level:4}")
    private int checkRetryDelayLevel;

    @Value("${app.order.mock-pay-delay-ms:0}")
    private long mockPayDelayMs;

    /** 从 FlashSaleHitEvent 建单：uk_order_no 幂等；随后调度支付超时检查 */
    @Transactional
    public boolean createFromHit(FlashSaleHitEvent evt) {
        boolean created = false;
        try {
            Order o = new Order();
            o.setOrderNo(evt.getOrderNo());
            o.setCustomerId(evt.getCustomerId());
            o.setProductId(evt.getProductId());
            o.setDropId(evt.getDropId());
            o.setStatus(PENDING_PAYMENT);
            o.setAmountCents(evt.getAmountCents());
            o.setExpireAt(toLocal(evt.getExpireAtMillis()));
            orderMapper.insert(o);
            created = true;
        } catch (DuplicateKeyException e) {
            log.info("订单已存在（幂等）orderNo={}", evt.getOrderNo());
        }
        // 无论首次还是重复，都调度一次超时检查（消费端幂等，重复无害）
        PaymentTimeoutCheck check = PaymentTimeoutCheck.builder()
                .orderNo(evt.getOrderNo())
                .customerId(evt.getCustomerId())
                .dropId(evt.getDropId())
                .productId(evt.getProductId())
                .build();
        sendDelayed(check, paymentDelayLevel);
        return created;
    }

    /** 延迟自消息：支付窗口结束时检查支付状态 */
    private void sendDelayed(PaymentTimeoutCheck check, int delayLevel) {
        rocketMQTemplate.syncSend(dest(Topics.ORDER_TIMEOUT, Topics.TAG_CHECK),
                MessageBuilder.withPayload(check).build(), 3000, delayLevel);
    }

    /** 支付超时检查：仍 PENDING_PAYMENT 且已过 expireAt → EXPIRED + 通知 flashsale 释放库存 */
    @Transactional
    public void checkTimeout(PaymentTimeoutCheck check) {
        Order o = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, check.getOrderNo()));
        if (o == null) {
            // 订单尚未落库（MQ 延迟），短延迟重试
            log.info("超时检查早于建单，重试 orderNo={}", check.getOrderNo());
            sendDelayed(check, checkRetryDelayLevel);
            return;
        }
        if (!PENDING_PAYMENT.equals(o.getStatus())) {
            return; // 已支付或已过期：幂等 no-op
        }
        if (LocalDateTime.now().isBefore(o.getExpireAt())) {
            // 时钟偏差/提前触发：重排到剩余时间
            long remainMs = java.time.Duration.between(LocalDateTime.now(), o.getExpireAt()).toMillis();
            sendDelayed(check, levelFor(remainMs));
            return;
        }
        o.setStatus(EXPIRED);
        orderMapper.updateById(o);
        emitRelease(o);
        log.info("订单支付超时 orderNo={} → 释放库存", o.getOrderNo());
    }

    /** 支付（模拟）：PENDING_PAYMENT 且未过期 → PAID，发 OrderPaidEvent（遥测） */
    @Transactional
    public OrderResponse pay(String orderNo, Long customerId) {
        Order o = requireOwner(orderNo, customerId);
        if (!PENDING_PAYMENT.equals(o.getStatus())) {
            throw ApiException.of(400, "订单状态不允许支付");
        }
        if (LocalDateTime.now().isAfter(o.getExpireAt())) {
            o.setStatus(EXPIRED);
            orderMapper.updateById(o);
            emitRelease(o);
            throw ApiException.of(400, "PAY_EXPIRED");
        }
        if (mockPayDelayMs > 0) {
            try {
                Thread.sleep(mockPayDelayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        o.setStatus(PAID);
        o.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(o);
        OrderPaidEvent evt = OrderPaidEvent.builder()
                .orderNo(o.getOrderNo()).customerId(o.getCustomerId())
                .productId(o.getProductId()).dropId(o.getDropId())
                .build();
        rocketMQTemplate.syncSend(dest(Topics.ORDER_PAID, Topics.TAG_PAID), evt);
        return toResponse(o);
    }

    /** 查询订单：404 表示尚未落库（CREATING），前端继续轮询 */
    public OrderResponse get(String orderNo, Long customerId) {
        Order o = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw ApiException.of(404, "订单尚未生成");
        }
        if (!o.getCustomerId().equals(customerId)) {
            throw ApiException.of(403, "无权查看该订单");
        }
        return toResponse(o);
    }

    public Page<OrderResponse> my(Long customerId, long page, long size) {
        Page<Order> pg = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<Order>lambdaQuery().eq(Order::getCustomerId, customerId)
                        .orderByDesc(Order::getCreatedAt));
        Page<OrderResponse> out = new Page<>(pg.getCurrent(), pg.getSize(), pg.getTotal());
        out.setRecords(pg.getRecords().stream().map(this::toResponse).toList());
        return out;
    }

    private void emitRelease(Order o) {
        OrderPaymentTimeoutEvent evt = OrderPaymentTimeoutEvent.builder()
                .orderNo(o.getOrderNo()).customerId(o.getCustomerId())
                .productId(o.getProductId()).dropId(o.getDropId())
                .build();
        rocketMQTemplate.syncSend(dest(Topics.ORDER_TIMEOUT_DONE, Topics.TAG_RELEASE), evt);
    }

    private Order requireOwner(String orderNo, Long customerId) {
        Order o = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw ApiException.of(404, "订单不存在");
        }
        if (!o.getCustomerId().equals(customerId)) {
            throw ApiException.of(403, "无权操作该订单");
        }
        return o;
    }

    private OrderResponse toResponse(Order o) {
        return OrderResponse.builder()
                .orderNo(o.getOrderNo()).status(o.getStatus())
                .productId(o.getProductId()).dropId(o.getDropId())
                .amountCents(o.getAmountCents()).expireAt(o.getExpireAt())
                .paidAt(o.getPaidAt()).createdAt(o.getCreatedAt())
                .build();
    }

    private static LocalDateTime toLocal(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    /** 剩余时间 → 延迟级别（与 broker.conf 的 messageDelayLevel 对应） */
    private static int levelFor(long remainMs) {
        long m = remainMs / 60_000;
        if (m <= 1) return 5;      // 1m
        if (m <= 2) return 6;      // 2m
        if (m <= 3) return 7;
        if (m <= 4) return 8;
        if (m <= 5) return 9;
        if (m <= 6) return 10;
        if (m <= 7) return 11;
        if (m <= 8) return 12;
        if (m <= 9) return 13;
        if (m <= 10) return 14;    // 10m
        if (m <= 15) return 15;    // 15m
        if (m <= 20) return 16;    // 20m
        if (m <= 30) return 17;    // 30m
        return 18;                 // 1h
    }

    private static String dest(String topic, String tag) {
        return topic + ":" + tag;
    }
}
