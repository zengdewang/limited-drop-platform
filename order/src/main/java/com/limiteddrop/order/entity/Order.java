package com.limiteddrop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 跨服务幂等键 */
    private String orderNo;
    private Long customerId;
    private Long productId;
    private Long dropId;
    /** PENDING_PAYMENT / PAID / EXPIRED */
    private String status;
    private Long amountCents;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
