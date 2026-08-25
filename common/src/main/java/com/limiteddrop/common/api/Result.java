package com.limiteddrop.common.api;

import lombok.Data;

/**
 * 统一响应信封：{code, message, data}。code=0 表示成功。
 */
@Data
public class Result<T> {

    public static final int OK = 0;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = OK;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(-1, message);
    }
}
