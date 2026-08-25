package com.limiteddrop.product.controller;

import com.limiteddrop.common.api.Result;
import com.limiteddrop.product.dto.DropRequest;
import com.limiteddrop.product.dto.DropResponse;
import com.limiteddrop.product.service.DropService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropService dropService;
    private final OpsGuard opsGuard;

    @GetMapping
    public Result<List<DropResponse>> list(@RequestParam(required = false) String status) {
        return Result.ok(dropService.list(status));
    }

    @GetMapping("/{id}")
    public Result<DropResponse> detail(@PathVariable Long id) {
        return Result.ok(dropService.detail(id));
    }

    /** ops：创建发售事件（触发 DropPublished → flashsale 同步本地会话） */
    @PostMapping
    public Result<DropResponse> create(@RequestHeader("X-Ops-Key") String opsKey,
                                       @Valid @RequestBody DropRequest req) {
        opsGuard.require(opsKey);
        return Result.ok(dropService.create(req));
    }
}
