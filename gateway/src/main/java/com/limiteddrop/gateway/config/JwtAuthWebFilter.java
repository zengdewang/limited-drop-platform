package com.limiteddrop.gateway.config;

import com.limiteddrop.common.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT 鉴权 WebFilter（在网关 GlobalFilter 之前执行）。
 * 白名单放行；否则校验 Bearer token，成功后注入 X-User-Id / X-Username 下游。
 * 顺序：JWT(-100) → XFF(-90) → SentinelGatewayFilter。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthWebFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getPath().value();
        if (isWhitelisted(req.getMethod(), path)) {
            return chain.filter(exchange);
        }
        String token = extractToken(req);
        if (token == null) {
            return unauthorized(exchange);
        }
        try {
            Long uid = jwtUtil.getCustomerId(token);
            String username = jwtUtil.getUsername(token);
            ServerHttpRequest mutated = req.mutate()
                    .header("X-User-Id", String.valueOf(uid))
                    .header("X-Username", username)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isWhitelisted(HttpMethod method, String path) {
        // CORS 预检不携带 JWT；必须在鉴权前放行，否则浏览器不会发送真正的购买请求。
        if (method == HttpMethod.OPTIONS) {
            return true;
        }
        // 静态演示页
        if (path.startsWith("/app/")) {
            return true;
        }
        // 认证
        if ("/api/user/auth/register".equals(path) || "/api/user/auth/login".equals(path)) {
            return true;
        }
        // 公开商品、发售与已审核评价；/api/product/reviews/my 必须经过 JWT。
        if (method == HttpMethod.GET && (path.equals("/api/product/products")
                || path.matches("/api/product/products/\\d+")
                || path.matches("/api/product/products/\\d+/reviews")
                || path.equals("/api/product/drops")
                || path.matches("/api/product/drops/\\d+"))) {
            return true;
        }
        if (method == HttpMethod.GET && path.matches("/api/flashsale/drops/\\d+/info")) {
            return true;
        }
        // ops 接口（下游用 X-Ops-Key 鉴权，网关放行）
        if (method == HttpMethod.POST && (path.equals("/api/product/products")
                || path.equals("/api/product/drops")
                || path.matches("/api/product/reviews/\\d+/moderate"))) {
            return true;
        }
        if (method == HttpMethod.GET && path.equals("/api/product/reviews/pending")) {
            return true;
        }
        if (method == HttpMethod.POST && path.matches("/api/flashsale/drops/\\d+/open|/api/flashsale/drops/\\d+/close")) {
            return true;
        }
        return false;
    }

    private String extractToken(ServerHttpRequest req) {
        String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
