package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * order → flashsale：支付超时，触发库存释放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderPaymentTimeoutEvent {
    private String orderNo;
    private Long customerId;
    private Long productId;
    private Long dropId;
}
