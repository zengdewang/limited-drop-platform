package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * product → flashsale：发售事件创建/更新，flashsale 存本地 drop_session 供预热与抢购。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DropPublished {
    private Long dropId;
    private Long productId;
    private String name;
    private String startTime;   // ISO-8601
    private String endTime;     // ISO-8601
    private Integer stock;
    private Long priceCents;
}
