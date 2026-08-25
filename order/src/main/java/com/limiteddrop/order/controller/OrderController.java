package com.limiteddrop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.api.Result;
import com.limiteddrop.order.dto.OrderResponse;
import com.limiteddrop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** JWT：轮询订单。404 = CREATING（尚未落库），前端继续轮询 */
    @GetMapping("/{orderNo}")
    public Result<OrderResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long customerId,
                                     @PathVariable String orderNo) {
        if (customerId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(orderService.get(orderNo, customerId));
    }

    /** JWT：模拟支付。错误：PAY_EXPIRED / NOT_OWNER */
    @PostMapping("/{orderNo}/pay")
    public Result<OrderResponse> pay(@RequestHeader(value = "X-User-Id", required = false) Long customerId,
                                     @PathVariable String orderNo) {
        if (customerId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(orderService.pay(orderNo, customerId));
    }

    /** JWT：我的订单 */
    @GetMapping("/my")
    public Result<Page<OrderResponse>> my(@RequestHeader(value = "X-User-Id", required = false) Long customerId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        if (customerId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(orderService.my(customerId, page, size));
    }
}
