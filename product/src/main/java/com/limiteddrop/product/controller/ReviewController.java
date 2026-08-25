package com.limiteddrop.product.controller;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.api.Result;
import com.limiteddrop.product.dto.ModerateRequest;
import com.limiteddrop.product.dto.ReviewRequest;
import com.limiteddrop.product.dto.ReviewResponse;
import com.limiteddrop.product.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final OpsGuard opsGuard;

    /** JWT（网关注入 X-User-Id）；校验订单已支付 + 归属本人 */
    @PostMapping("/reviews")
    public Result<ReviewResponse> create(@RequestHeader(value = "X-User-Id", required = false) Long customerId,
                                         @Valid @RequestBody ReviewRequest req) {
        if (customerId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(reviewService.create(customerId, req));
    }

    @GetMapping("/products/{productId}/reviews")
    public Result<List<ReviewResponse>> listByProduct(@PathVariable Long productId) {
        return Result.ok(reviewService.listByProduct(productId));
    }

    /** ops：待人工复核列表 */
    @GetMapping("/reviews/pending")
    public Result<List<ReviewResponse>> pending(@RequestHeader("X-Ops-Key") String opsKey) {
        opsGuard.require(opsKey);
        return Result.ok(reviewService.listPending());
    }

    /** ops：人工复核。APPROVE → 进知识库；对已 APPROVED 的 REJECT → 从知识库删除 */
    @PostMapping("/reviews/{id}/moderate")
    public Result<Void> moderate(@RequestHeader("X-Ops-Key") String opsKey,
                                 @PathVariable Long id,
                                 @Valid @RequestBody ModerateRequest req) {
        opsGuard.require(opsKey);
        reviewService.moderate(id, req.getAction());
        return Result.ok();
    }
}
