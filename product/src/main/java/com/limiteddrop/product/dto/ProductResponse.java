package com.limiteddrop.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String brand;
    private String name;
    private String category;
    private String imageUrl;
    /** 最近一场发售价格（分），无发售则为 null */
    private Long priceCents;
}
