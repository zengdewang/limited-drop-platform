package com.limiteddrop.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebExceptionHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关限流：对 flashsale 路由按 IP（X-Real-Ip）与账号（X-User-Id）限流。
 * 手动注册 filter（adapter 1.8.8 无 spring.factories 自动配置）+ 自定义 429 处理器，规则启动加载（QPS 可配）。
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @Value("${app.sentinel.per-ip-qps:5}")
    private int perIpQps;

    @Value("${app.sentinel.per-account-qps:10}")
    private int perAccountQps;

    @Bean
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    public WebExceptionHandler sentinelBlockWebExceptionHandler() {
        return new SentinelBlockWebExceptionHandler();
    }

    @Bean
    public ApplicationRunner loadSentinelRules() {
        return args -> {
            // Sentinel 网关资源 = 路由 ID（yml 中 route 的 id: flashsale）
            Set<GatewayFlowRule> rules = new HashSet<>();

            GatewayFlowRule ipRule = new GatewayFlowRule("flashsale");
            ipRule.setCount(perIpQps);
            ipRule.setIntervalSec(1);
            ipRule.setParamItem(new GatewayParamFlowItem()
                    .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                    .setFieldName("X-Real-Ip"));
            rules.add(ipRule);

            GatewayFlowRule accountRule = new GatewayFlowRule("flashsale");
            accountRule.setCount(perAccountQps);
            accountRule.setIntervalSec(1);
            accountRule.setParamItem(new GatewayParamFlowItem()
                    .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER)
                    .setFieldName("X-User-Id"));
            rules.add(accountRule);

            GatewayRuleManager.loadRules(rules);
            log.info("Sentinel 规则已加载: per-ip={} qps, per-account={} qps", perIpQps, perAccountQps);
        };
    }
}
