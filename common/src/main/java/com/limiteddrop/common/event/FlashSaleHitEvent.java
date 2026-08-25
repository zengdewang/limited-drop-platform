package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * flashsale → order：抢购命中，异步创建订单。幂等键 = orderNo。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlashSaleHitEvent {
    private String orderNo;
    private Long customerId;
    private Long productId;
    private Long dropId;
    private Long amountCents;
    private Long expireAtMillis;   // payment window deadline (epoch millis)
}
