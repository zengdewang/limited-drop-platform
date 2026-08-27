package com.limiteddrop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank
    private String brand;

    @NotBlank
    private String name;

    private String category;

    @NotBlank
    @Pattern(regexp = "https?://.+", message = "图片地址必须以 http:// 或 https:// 开头")
    private String imageUrl;

    @NotBlank
    private String officialDoc;
}
