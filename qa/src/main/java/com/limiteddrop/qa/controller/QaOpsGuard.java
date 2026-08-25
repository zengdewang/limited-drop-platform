package com.limiteddrop.qa.controller;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.qa.config.QaProperties;
import org.springframework.stereotype.Component;

@Component
public class QaOpsGuard {
    private final QaProperties properties;

    public QaOpsGuard(QaProperties properties) {
        this.properties = properties;
    }

    public void require(String key) {
        if (key == null || properties.getOpsKey() == null || !properties.getOpsKey().equals(key)) {
            throw ApiException.of(401, "ops key 无效");
        }
    }
}
