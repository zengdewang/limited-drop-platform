package com.limiteddrop.flashsale.controller;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.common.api.Result;
import com.limiteddrop.flashsale.dto.BuyResponse;
import com.limiteddrop.flashsale.dto.InfoResponse;
import com.limiteddrop.flashsale.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flashsale")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @Value("${app.ops-key}")
    private String opsKey;

    /** 公开：轮询库存/状态（Redis 优先） */
    @GetMapping("/drops/{dropId}/info")
    public Result<InfoResponse> info(@PathVariable Long dropId) {
        return Result.ok(flashSaleService.info(dropId));
    }

    /** ops：开售（预热库存 + 开售标记） */
    @PostMapping("/drops/{dropId}/open")
    public Result<Void> open(@RequestHeader("X-Ops-Key") String opsKey, @PathVariable Long dropId) {
        opsGuard(opsKey);
        flashSaleService.open(dropId);
        return Result.ok();
    }

    /** ops：关停 */
    @PostMapping("/drops/{dropId}/close")
    public Result<Void> close(@RequestHeader("X-Ops-Key") String opsKey, @PathVariable Long dropId) {
        opsGuard(opsKey);
        flashSaleService.close(dropId);
        return Result.ok();
    }

    /** 抢购热路径（JWT）。code: 0 命中 / -1 售罄 / -2 重复 / -3 未开售；Sentinel 限流 → 429 */
    @PostMapping("/drops/{dropId}/buy")
    public Result<BuyResponse> buy(@RequestHeader(value = "X-User-Id", required = false) Long customerId,
                                   @PathVariable Long dropId) {
        if (customerId == null) {
            throw ApiException.of(401, "未登录");
        }
        return Result.ok(flashSaleService.buy(dropId, customerId));
    }

    private void opsGuard(String provided) {
        if (!opsKey.equals(provided)) {
            throw ApiException.of(401, "ops key 无效");
        }
    }
}
