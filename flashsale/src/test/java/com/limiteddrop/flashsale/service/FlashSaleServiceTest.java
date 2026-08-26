package com.limiteddrop.flashsale.service;

import com.limiteddrop.common.redis.FlashSaleKey;
import com.limiteddrop.flashsale.entity.DropSession;
import com.limiteddrop.flashsale.mapper.DropSessionMapper;
import com.limiteddrop.flashsale.mq.FlashSaleHitPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlashSaleServiceTest {

    @Test
    void reportsEndedWhenDatabaseIsOpenButRedisOpenKeyHasExpired() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any(String.class))).thenReturn(null);

        DropSession session = new DropSession();
        session.setDropId(2L);
        session.setStock(10);
        session.setStatus("OPEN");
        session.setStartTime(LocalDateTime.now().minusHours(2));
        session.setEndTime(LocalDateTime.now().minusMinutes(1));

        DropSessionMapper mapper = mock(DropSessionMapper.class);
        when(mapper.selectOne(any())).thenReturn(session);

        FlashSaleService service = new FlashSaleService(
                redis,
                mock(DefaultRedisScript.class),
                mock(DefaultRedisScript.class),
                mapper,
                mock(FlashSaleHitPublisher.class),
                mock(FlashHitLogWriter.class));

        var response = service.info(2L);

        assertEquals("ENDED", response.getStatus());
        assertNull(response.getRemaining());
    }

    @Test
    void doesNotReportOpenWhenRedisInventoryIsMissingDuringWindow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(FlashSaleKey.open(2L))).thenReturn("1");
        when(values.get(FlashSaleKey.inv(2L))).thenReturn(null);

        DropSession session = new DropSession();
        session.setDropId(2L);
        session.setStock(10);
        session.setStatus("OPEN");
        session.setStartTime(LocalDateTime.now().minusMinutes(1));
        session.setEndTime(LocalDateTime.now().plusMinutes(30));

        DropSessionMapper mapper = mock(DropSessionMapper.class);
        when(mapper.selectOne(any())).thenReturn(session);

        FlashSaleService service = new FlashSaleService(
                redis,
                mock(DefaultRedisScript.class),
                mock(DefaultRedisScript.class),
                mapper,
                mock(FlashSaleHitPublisher.class),
                mock(FlashHitLogWriter.class));

        var response = service.info(2L);

        assertEquals("SCHEDULED", response.getStatus());
        assertNull(response.getRemaining());
    }
}
