package com.limiteddrop.product.mq;

import com.limiteddrop.common.event.DropPublished;
import com.limiteddrop.common.event.ProductDocPublished;
import com.limiteddrop.common.event.ReviewModerated;
import com.limiteddrop.common.event.ReviewUnmoderated;
import com.limiteddrop.common.mq.Topics;
import com.limiteddrop.product.entity.Drop;
import com.limiteddrop.product.entity.Product;
import com.limiteddrop.product.entity.Review;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * product 事件发射：官方文档发布、发售创建、评价审核结果。
 */
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    public void productDocPublished(Product p) {
        ProductDocPublished evt = ProductDocPublished.builder()
                .productId(p.getId())
                .brand(p.getBrand())
                .name(p.getName())
                .category(p.getCategory())
                .officialDoc(p.getOfficialDoc())
                .build();
        rocketMQTemplate.syncSend(dest(Topics.PRODUCT_DOC, Topics.TAG_DOC_PUBLISHED), evt);
    }

    public void dropPublished(Drop d) {
        DropPublished evt = DropPublished.builder()
                .dropId(d.getId())
                .productId(d.getProductId())
                .name(d.getName())
                .startTime(d.getStartTime() == null ? null : d.getStartTime().toString())
                .endTime(d.getEndTime() == null ? null : d.getEndTime().toString())
                .stock(d.getStock())
                .priceCents(d.getPriceCents())
                .build();
        rocketMQTemplate.syncSend(dest(Topics.DROP_PUBLISHED, Topics.TAG_DROP_PUBLISHED), evt);
    }

    public void reviewModerated(Review r) {
        ReviewModerated evt = ReviewModerated.builder()
                .reviewId(r.getId())
                .productId(r.getProductId())
                .rating(r.getRating())
                .content(r.getContent())
                .build();
        rocketMQTemplate.syncSend(dest(Topics.REVIEW_MODERATED, Topics.TAG_MODERATED), evt);
    }

    public void reviewUnmoderated(Long reviewId, Long productId) {
        ReviewUnmoderated evt = ReviewUnmoderated.builder()
                .reviewId(reviewId)
                .productId(productId)
                .build();
        rocketMQTemplate.syncSend(dest(Topics.REVIEW_MODERATED, Topics.TAG_UNMODERATED), evt);
    }

    private static String dest(String topic, String tag) {
        return topic + ":" + tag;
    }
}
