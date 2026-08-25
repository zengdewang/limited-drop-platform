package com.limiteddrop.user.controller;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.api.Result;
import com.limiteddrop.user.dto.AuthResponse;
import com.limiteddrop.user.dto.LoginRequest;
import com.limiteddrop.user.dto.MeResponse;
import com.limiteddrop.user.dto.RegisterRequest;
import com.limiteddrop.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/auth/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /** 网关解析 JWT 后注入 X-User-Id；直连时也可传 Authorization: Bearer。 */
    @GetMapping("/me")
    public Result<MeResponse> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(authService.me(userId));
    }
}
