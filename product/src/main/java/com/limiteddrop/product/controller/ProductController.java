package com.limiteddrop.product.controller;

import com.limiteddrop.common.api.Result;
import com.limiteddrop.product.dto.ProductDetailResponse;
import com.limiteddrop.product.dto.ProductRequest;
import com.limiteddrop.product.dto.ProductResponse;
import com.limiteddrop.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final OpsGuard opsGuard;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long size) {
        return Result.ok(productService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    /** ops：新建商品（触发 ProductDocPublished → qa 建索引） */
    @PostMapping
    public Result<ProductResponse> create(@RequestHeader("X-Ops-Key") String opsKey,
                                          @Valid @RequestBody ProductRequest req) {
        opsGuard.require(opsKey);
        return Result.ok(productService.create(req));
    }
}
