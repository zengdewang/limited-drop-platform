package com.limiteddrop.product.controller;

import com.limiteddrop.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ops 接口鉴权：X-Ops-Key 头必须等于配置的共享密钥（v1 无管理员表）。
 */
@Component
public class OpsGuard {

    @Value("${app.ops-key}")
    private String opsKey;

    public void require(String provided) {
        if (!opsKey.equals(provided)) {
            throw ApiException.of(401, "ops key 无效");
        }
    }
}
