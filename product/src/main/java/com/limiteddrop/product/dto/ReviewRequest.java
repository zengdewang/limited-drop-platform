package com.limiteddrop.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotBlank
    private String orderNo;

    @Min(1)
    @Max(5)
    private int rating;

    @NotBlank
    private String content;
}
