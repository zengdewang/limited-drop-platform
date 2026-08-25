package com.limiteddrop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 跨服务幂等键：一单一评 */
    private String orderNo;
    private Long customerId;
    private Long productId;
    private Integer rating;
    private String content;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private LocalDateTime moderatedAt;
    private LocalDateTime createdAt;
}
