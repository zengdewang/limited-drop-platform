package com.limiteddrop.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private Long id;
    private String brand;
    private String name;
    private String category;
    private String imageUrl;
    private String officialDoc;
    private Long priceCents;
}
