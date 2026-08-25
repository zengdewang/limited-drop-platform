package com.limiteddrop.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private String orderNo;
    private Long productId;
    private Integer rating;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
