package com.limiteddrop.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewStatusResponse {
    private String orderNo;
    private Long productId;
    private boolean eligible;
    private boolean reviewed;
    private String reviewStatus;
    private Integer rating;
    private String content;
}
