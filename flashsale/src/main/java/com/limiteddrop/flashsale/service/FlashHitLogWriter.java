package com.limiteddrop.flashsale.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.flashsale.entity.FlashHitLog;
import com.limiteddrop.flashsale.mapper.FlashHitLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 抢购审计日志的异步写入，绝不影响热路径延迟。
 * 一单一行（uk_order_no）：RESERVED → RELEASED / PAID 用 UPDATE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashHitLogWriter {

    private final FlashHitLogMapper flashHitLogMapper;

    @Async("asyncLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReserved(String orderNo, Long customerId, Long dropId) {
        try {
            FlashHitLog row = new FlashHitLog();
            row.setOrderNo(orderNo);
            row.setCustomerId(customerId);
            row.setDropId(dropId);
            row.setStatus("RESERVED");
            flashHitLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("recordReserved failed orderNo={}", orderNo, e);
        }
    }

    @Async("asyncLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReleased(String orderNo, Long customerId, Long dropId) {
        try {
            FlashHitLog row = flashHitLogMapper.selectOne(
                    Wrappers.<FlashHitLog>lambdaQuery().eq(FlashHitLog::getOrderNo, orderNo));
            if (row == null) {
                FlashHitLog r = new FlashHitLog();
                r.setOrderNo(orderNo);
                r.setCustomerId(customerId);
                r.setDropId(dropId);
                r.setStatus("RELEASED");
                flashHitLogMapper.insert(r);
            } else {
                row.setStatus("RELEASED");
                flashHitLogMapper.updateById(row);
            }
        } catch (Exception e) {
            log.warn("markReleased failed orderNo={}", orderNo, e);
        }
    }

    @Async("asyncLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaid(String orderNo) {
        try {
            FlashHitLog row = flashHitLogMapper.selectOne(
                    Wrappers.<FlashHitLog>lambdaQuery().eq(FlashHitLog::getOrderNo, orderNo));
            if (row != null) {
                row.setStatus("PAID");
                flashHitLogMapper.updateById(row);
            }
        } catch (Exception e) {
            log.warn("markPaid failed orderNo={}", orderNo, e);
        }
    }
}

