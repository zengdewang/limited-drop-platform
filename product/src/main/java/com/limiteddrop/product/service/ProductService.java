package com.limiteddrop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.product.dto.ProductDetailResponse;
import com.limiteddrop.product.dto.ProductRequest;
import com.limiteddrop.product.dto.ProductResponse;
import com.limiteddrop.product.entity.Drop;
import com.limiteddrop.product.entity.Product;
import com.limiteddrop.product.mapper.DropMapper;
import com.limiteddrop.product.mapper.ProductMapper;
import com.limiteddrop.product.mq.ProductEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final DropMapper dropMapper;
    private final ProductEventPublisher publisher;

    @Transactional
    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        p.setBrand(req.getBrand());
        p.setName(req.getName());
        p.setCategory(req.getCategory());
        p.setImageUrl(req.getImageUrl());
        p.setOfficialDoc(req.getOfficialDoc());
        productMapper.insert(p);
        publisher.productDocPublished(p);
        return toResponse(p);
    }

    public Page<ProductResponse> list(long page, long size) {
        Page<Product> pg = productMapper.selectPage(new Page<>(page, size), null);
        Page<ProductResponse> out = new Page<>(pg.getCurrent(), pg.getSize(), pg.getTotal());
        out.setRecords(pg.getRecords().stream().map(this::toResponse).toList());
        return out;
    }

    public ProductDetailResponse detail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw ApiException.of(404, "商品不存在");
        }
        return ProductDetailResponse.builder()
                .id(p.getId()).brand(p.getBrand()).name(p.getName())
                .category(p.getCategory()).imageUrl(p.getImageUrl())
                .officialDoc(p.getOfficialDoc())
                .priceCents(latestPrice(id))
                .build();
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId()).brand(p.getBrand()).name(p.getName())
                .category(p.getCategory()).imageUrl(p.getImageUrl())
                .priceCents(latestPrice(p.getId()))
                .build();
    }

    /** 最近一场发售的价格（分）；无发售返回 null。商品量小，N+1 可接受。 */
    private Long latestPrice(Long productId) {
        List<Drop> drops = dropMapper.selectList(
                Wrappers.<Drop>lambdaQuery()
                        .eq(Drop::getProductId, productId)
                        .orderByDesc(Drop::getStartTime)
                        .last("limit 1"));
        return drops.isEmpty() ? null : drops.get(0).getPriceCents();
    }
}
