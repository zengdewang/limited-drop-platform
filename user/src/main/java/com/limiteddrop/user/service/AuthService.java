package com.limiteddrop.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.auth.JwtUtil;
import com.limiteddrop.user.dto.AuthResponse;
import com.limiteddrop.user.dto.LoginRequest;
import com.limiteddrop.user.dto.MeResponse;
import com.limiteddrop.user.dto.RegisterRequest;
import com.limiteddrop.user.entity.Customer;
import com.limiteddrop.user.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomerMapper customerMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest req) {
        Long exists = customerMapper.selectCount(
                Wrappers.<Customer>lambdaQuery().eq(Customer::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw ApiException.of(400, "用户名已存在");
        }
        Customer c = new Customer();
        c.setUsername(req.getUsername());
        c.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        c.setMemberLevel("NORMAL");
        customerMapper.insert(c);
        return toAuth(c);
    }

    public AuthResponse login(LoginRequest req) {
        Customer c = customerMapper.selectOne(
                Wrappers.<Customer>lambdaQuery().eq(Customer::getUsername, req.getUsername()));
        if (c == null || !passwordEncoder.matches(req.getPassword(), c.getPasswordHash())) {
            throw ApiException.of(401, "用户名或密码错误");
        }
        return toAuth(c);
    }

    public MeResponse me(Long customerId) {
        Customer c = customerMapper.selectById(customerId);
        if (c == null) {
            throw ApiException.of(404, "用户不存在");
        }
        return MeResponse.builder()
                .userId(c.getId())
                .username(c.getUsername())
                .memberLevel(c.getMemberLevel())
                .build();
    }

    private AuthResponse toAuth(Customer c) {
        String token = jwtUtil.createToken(c.getId(), c.getUsername());
        return AuthResponse.builder()
                .token(token)
                .userId(c.getId())
                .username(c.getUsername())
                .memberLevel(c.getMemberLevel())
                .build();
    }
}
