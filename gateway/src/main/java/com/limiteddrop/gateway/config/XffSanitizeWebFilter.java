package com.limiteddrop.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * XFF 清洗：取 X-Forwarded-For 首个值 → X-Real-Ip，供 Sentinel 按 IP 限流。
 * 必须在 SentinelGatewayFilter 之前执行（WebFilter 先于 GlobalFilter）。
 */
@Component
public class XffSanitizeWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        String realIp;
        if (xff != null && !xff.isBlank()) {
            realIp = xff.split(",")[0].trim();
        } else if (req.getRemoteAddress() != null) {
            realIp = req.getRemoteAddress().getAddress().getHostAddress();
        } else {
            realIp = "unknown";
        }
        ServerHttpRequest mutated = req.mutate().header("X-Real-Ip", realIp).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
