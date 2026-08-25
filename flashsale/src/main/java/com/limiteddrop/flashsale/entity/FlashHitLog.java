package com.limiteddrop.flashsale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("flash_hit_log")
public class FlashHitLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long customerId;
    private Long dropId;
    /** RESERVED / PAID / RELEASED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
