package com.limiteddrop.gateway.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 拦截响应：捕获 BlockException → HTTP 429 JSON。
 * 必须比 Spring 默认错误处理器（@Order(-1)）优先级高，故 Order=-2。
 * （adapter 自带的 SentinelGatewayBlockExceptionHandler 未实现 Ordered，会被默认 500 处理器抢先。）
 */
public class SentinelBlockWebExceptionHandler implements WebExceptionHandler, Ordered {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof BlockException) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
        return Mono.error(ex);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
