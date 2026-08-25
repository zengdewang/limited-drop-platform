package com.limiteddrop.gateway.config;

import com.limiteddrop.common.auth.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.ttl-millis}") long ttlMillis) {
        return new JwtUtil(secret, ttlMillis);
    }
}
