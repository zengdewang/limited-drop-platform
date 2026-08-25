package com.limiteddrop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 4, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "仅允许字母、数字、下划线")
    private String username;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;
}
