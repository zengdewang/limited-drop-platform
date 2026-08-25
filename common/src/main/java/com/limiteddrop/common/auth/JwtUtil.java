package com.limiteddrop.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具。各服务用 @Bean 注入（secret 来自配置）。
 * 声明中携带 uid / username；网关解析后注入 X-User-Id / X-Username 下游。
 */
public class JwtUtil {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_USERNAME = "username";

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(String secret, long ttlMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMillis;
    }

    public String createToken(long customerId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(customerId))
                .claim(CLAIM_UID, customerId)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getCustomerId(String token) {
        Claims claims = parse(token);
        Object uid = claims.get(CLAIM_UID);
        if (uid instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(uid));
    }

    public String getUsername(String token) {
        return parse(token).get(CLAIM_USERNAME, String.class);
    }
}
