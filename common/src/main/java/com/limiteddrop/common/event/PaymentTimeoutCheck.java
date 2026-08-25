package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * order 内部自消息：延迟到支付窗口截止时检查是否支付。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentTimeoutCheck {
    private String orderNo;
    private Long customerId;
    private Long dropId;
    private Long productId;
}
