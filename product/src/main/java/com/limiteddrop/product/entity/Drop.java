package com.limiteddrop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`drop`")
public class Drop {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer stock;
    private Long priceCents;
    /** SCHEDULED / OPEN / ENDED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
