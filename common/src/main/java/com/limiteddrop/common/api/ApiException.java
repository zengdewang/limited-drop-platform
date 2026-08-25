package com.limiteddrop.common.api;

import lombok.Getter;

/**
 * 业务异常，携带业务码。由各服务自己的 @RestControllerAdvice 捕获并转成 Result。
 */
@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static ApiException of(int code, String message) {
        return new ApiException(code, message);
    }
}
