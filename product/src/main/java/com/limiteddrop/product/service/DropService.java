package com.limiteddrop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.product.dto.DropRequest;
import com.limiteddrop.product.dto.DropResponse;
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
public class DropService {

    private final DropMapper dropMapper;
    private final ProductMapper productMapper;
    private final ProductEventPublisher publisher;

    @Transactional
    public DropResponse create(DropRequest req) {
        if (productMapper.selectById(req.getProductId()) == null) {
            throw ApiException.of(404, "商品不存在");
        }
        Drop d = new Drop();
        d.setProductId(req.getProductId());
        d.setName(req.getName());
        d.setStartTime(req.getStartTime());
        d.setEndTime(req.getEndTime());
        d.setStock(req.getStock());
        d.setPriceCents(req.getPriceCents());
        d.setStatus("SCHEDULED");
        dropMapper.insert(d);
        publisher.dropPublished(d);
        return toResponse(d);
    }

    public List<DropResponse> list(String status) {
        var q = Wrappers.<Drop>lambdaQuery()
                .orderByAsc(Drop::getStartTime);
        if (status != null && !status.isBlank()) {
            q.eq(Drop::getStatus, status);
        }
        return dropMapper.selectList(q).stream().map(this::toResponse).toList();
    }

    public DropResponse detail(Long id) {
        Drop d = dropMapper.selectById(id);
        if (d == null) {
            throw ApiException.of(404, "发售不存在");
        }
        return toResponse(d);
    }

    private DropResponse toResponse(Drop d) {
        Product p = productMapper.selectById(d.getProductId());
        return DropResponse.builder()
                .id(d.getId())
                .productId(d.getProductId())
                .productName(p == null ? null : p.getName())
                .brand(p == null ? null : p.getBrand())
                .name(d.getName())
                .startTime(d.getStartTime())
                .endTime(d.getEndTime())
                .stock(d.getStock())
                .priceCents(d.getPriceCents())
                .status(d.getStatus())
                .build();
    }
}
