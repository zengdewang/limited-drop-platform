package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * order → flashsale：订单支付成功（仅遥测/对账，库存不变）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderPaidEvent {
    private String orderNo;
    private Long customerId;
    private Long productId;
    private Long dropId;
}
