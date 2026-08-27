package com.limiteddrop.gateway.config;

import com.limiteddrop.common.auth.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JwtAuthWebFilterTest {

    @Test
    void allowsCorsPreflightWithoutJwt() {
        JwtAuthWebFilter filter = new JwtAuthWebFilter(mock(JwtUtil.class));
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.OPTIONS, "/api/flashsale/drops/2/buy")
                .header("Origin", "http://localhost:4173")
                .header("Access-Control-Request-Method", HttpMethod.POST.name())
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void protectsCurrentCustomersReviewStatuses() {
        JwtAuthWebFilter filter = new JwtAuthWebFilter(mock(JwtUtil.class));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/product/reviews/my?orderNos=order-1").build());
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, next -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void corsHeadersArePresentOnJwtFailure() {
        CorsConfig corsConfig = new CorsConfig();
        JwtAuthWebFilter jwtFilter = new JwtAuthWebFilter(mock(JwtUtil.class));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("http://localhost:8080/api/orders/my")
                .header(HttpHeaders.ORIGIN, "http://localhost:4173")
                .build());

        corsConfig.corsWebFilter().filter(exchange,
                next -> jwtFilter.filter(next, ignored -> Mono.empty())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("http://localhost:4173",
                exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
