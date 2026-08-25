package com.limiteddrop.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModerateRequest {

    /** APPROVE | REJECT */
    @NotBlank
    private String action;
}
