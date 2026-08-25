package com.limiteddrop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank
    private String brand;

    @NotBlank
    private String name;

    private String category;

    @NotBlank
    private String officialDoc;
}
