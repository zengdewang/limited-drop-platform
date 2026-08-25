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
public class DropResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String brand;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer stock;
    private Long priceCents;
    private String status;
}
