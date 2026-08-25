package com.limiteddrop.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyResponse {
    /** 0 命中 / -1 售罄 / -2 重复（已抢到，返回已有 orderNo）/ -3 未开售 */
    private int code;
    private String orderNo;
    private Long remaining;
}
