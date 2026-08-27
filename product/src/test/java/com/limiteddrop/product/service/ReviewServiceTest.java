package com.limiteddrop.product.service;

import com.limiteddrop.common.api.ApiException;
import com.limiteddrop.product.dto.MyReviewStatusResponse;
import com.limiteddrop.product.dto.ReviewRequest;
import com.limiteddrop.product.entity.PaidOrder;
import com.limiteddrop.product.entity.Review;
import com.limiteddrop.product.mapper.PaidOrderMapper;
import com.limiteddrop.product.mapper.ReviewMapper;
import com.limiteddrop.product.mq.ProductEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private ReviewMapper reviewMapper;
    private PaidOrderMapper paidOrderMapper;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ReviewMapper.class);
        paidOrderMapper = mock(PaidOrderMapper.class);
        reviewService = new ReviewService(reviewMapper, paidOrderMapper, mock(ProductEventPublisher.class));
        ReflectionTestUtils.setField(reviewService, "minLength", 20);
        ReflectionTestUtils.setField(reviewService, "sensitiveWords", "");
    }

    @Test
    void mapsPaidEligibilityAndExistingReviewForRequestedOrders() {
        PaidOrder paidOrder = new PaidOrder();
        paidOrder.setOrderNo("paid-order");
        paidOrder.setCustomerId(7L);
        paidOrder.setProductId(4L);
        Review review = new Review();
        review.setOrderNo("reviewed-order");
        review.setCustomerId(7L);
        review.setProductId(5L);
        review.setStatus("APPROVED");
        review.setRating(5);
        review.setContent("包装和商品状态都很好");
        when(paidOrderMapper.selectList(any())).thenReturn(List.of(paidOrder));
        when(reviewMapper.selectList(any())).thenReturn(List.of(review));

        List<MyReviewStatusResponse> result = reviewService.myStatuses(
                7L, List.of("paid-order", "reviewed-order", "syncing-order"));

        assertTrue(result.get(0).isEligible());
        assertFalse(result.get(0).isReviewed());
        assertTrue(result.get(1).isReviewed());
        assertEquals("APPROVED", result.get(1).getReviewStatus());
        assertFalse(result.get(2).isEligible());
    }

    @Test
    void convertsConcurrentUniqueKeyFailureToBusinessError() {
        PaidOrder paidOrder = new PaidOrder();
        paidOrder.setOrderNo("paid-order");
        paidOrder.setCustomerId(7L);
        paidOrder.setProductId(4L);
        when(paidOrderMapper.selectOne(any())).thenReturn(paidOrder);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.insert(any(Review.class))).thenThrow(new DuplicateKeyException("uk_order"));
        ReviewRequest request = new ReviewRequest();
        request.setOrderNo("paid-order");
        request.setRating(5);
        request.setContent("商品状态很好，包装也非常细致");

        ApiException error = assertThrows(ApiException.class, () -> reviewService.create(7L, request));

        assertEquals(400, error.getCode());
        assertEquals("该订单已评价过", error.getMessage());
    }
}
