package com.limiteddrop.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.product.dto.ReviewRequest;
import com.limiteddrop.product.dto.ReviewResponse;
import com.limiteddrop.product.entity.PaidOrder;
import com.limiteddrop.product.entity.Review;
import com.limiteddrop.product.mapper.PaidOrderMapper;
import com.limiteddrop.product.mapper.ReviewMapper;
import com.limiteddrop.product.mq.ProductEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价 + Moderation 状态机（PENDING/APPROVED/REJECTED）。
 * 审核通过才进入 RAG 知识库（发 ReviewModerated → qa），见 ADR-0004。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final ReviewMapper reviewMapper;
    private final PaidOrderMapper paidOrderMapper;
    private final ProductEventPublisher publisher;

    @Value("${app.moderation.min-length:20}")
    private int minLength;

    @Value("${app.moderation.sensitive-words:}")
    private String sensitiveWords;

    @Transactional
    public ReviewResponse create(Long customerId, ReviewRequest req) {
        PaidOrder po = paidOrderMapper.selectOne(Wrappers.<PaidOrder>lambdaQuery()
                .eq(PaidOrder::getOrderNo, req.getOrderNo())
                .eq(PaidOrder::getCustomerId, customerId));
        if (po == null) {
            throw ApiException.of(403, "订单不存在或未支付，无法评价");
        }
        Long dup = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderNo, req.getOrderNo()));
        if (dup != null && dup > 0) {
            throw ApiException.of(400, "该订单已评价过");
        }

        Review r = new Review();
        r.setOrderNo(req.getOrderNo());
        r.setCustomerId(customerId);
        r.setProductId(po.getProductId());
        r.setRating(req.getRating());
        r.setContent(req.getContent());
        r.setStatus(autoModerate(req));
        if (!PENDING.equals(r.getStatus())) {
            r.setModeratedAt(LocalDateTime.now());
        }
        reviewMapper.insert(r);
        if (APPROVED.equals(r.getStatus())) {
            publisher.reviewModerated(r);
        }
        return toResponse(r);
    }

    public List<ReviewResponse> listByProduct(Long productId) {
        return reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                        .eq(Review::getProductId, productId)
                        .eq(Review::getStatus, APPROVED)
                        .orderByDesc(Review::getCreatedAt))
                .stream().map(this::toResponse).toList();
    }

    public List<ReviewResponse> listPending() {
        return reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, PENDING)
                        .orderByAsc(Review::getCreatedAt))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void moderate(Long reviewId, String action) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) {
            throw ApiException.of(404, "评价不存在");
        }
        if ("APPROVE".equalsIgnoreCase(action)) {
            if (!APPROVED.equals(r.getStatus())) {
                r.setStatus(APPROVED);
                r.setModeratedAt(LocalDateTime.now());
                reviewMapper.updateById(r);
                publisher.reviewModerated(r);
            }
        } else if ("REJECT".equalsIgnoreCase(action)) {
            boolean wasApproved = APPROVED.equals(r.getStatus());
            if (!REJECTED.equals(r.getStatus())) {
                r.setStatus(REJECTED);
                r.setModeratedAt(LocalDateTime.now());
                reviewMapper.updateById(r);
            }
            if (wasApproved) {
                publisher.reviewUnmoderated(r.getId(), r.getProductId());
            }
        } else {
            throw ApiException.of(400, "action 必须为 APPROVE 或 REJECT");
        }
    }

    /** 自动审核：命中敏感词→拒绝；长度达标→通过；否则待人工复核。 */
    private String autoModerate(ReviewRequest req) {
        if (containsSensitive(req.getContent())) {
            return REJECTED;
        }
        if (req.getContent().length() >= minLength) {
            return APPROVED;
        }
        return PENDING;
    }

    private boolean containsSensitive(String content) {
        if (sensitiveWords == null || sensitiveWords.isBlank()) {
            return false;
        }
        for (String w : sensitiveWords.split(",")) {
            if (!w.isBlank() && content.contains(w.trim())) {
                return true;
            }
        }
        return false;
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .orderNo(r.getOrderNo())
                .productId(r.getProductId())
                .rating(r.getRating())
                .content(r.getContent())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
