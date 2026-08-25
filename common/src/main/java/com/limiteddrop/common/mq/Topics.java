package com.limiteddrop.common.mq;

/**
 * RocketMQ topic / tag 常量。约定：topic 一个事件流，tag 区分事件类型。
 */
public final class Topics {

    // flashsale → order：抢购命中，异步建单
    public static final String FLASH_SALE_HIT = "DROP_ORDER";
    public static final String TAG_HIT = "HIT";

    // product → qa：官方文档发布/更新
    public static final String PRODUCT_DOC = "PRODUCT_DOC";
    public static final String TAG_DOC_PUBLISHED = "DOC_PUBLISHED";

    // product → qa：评价审核通过/撤回
    public static final String REVIEW_MODERATED = "REVIEW_MODERATED";
    public static final String TAG_MODERATED = "MODERATED";
    public static final String TAG_UNMODERATED = "UNMODERATED";

    // product → flashsale：发售创建/更新
    public static final String DROP_PUBLISHED = "DROP_PUBLISHED";
    public static final String TAG_DROP_PUBLISHED = "DROP";

    // order → flashsale：支付成功（遥测）
    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String TAG_PAID = "PAID";

    // order 内部：延迟检查支付超时（自消息）
    public static final String ORDER_TIMEOUT = "ORDER_TIMEOUT";
    public static final String TAG_CHECK = "CHECK";

    // order → flashsale：支付超时，释放库存
    public static final String ORDER_TIMEOUT_DONE = "ORDER_TIMEOUT_DONE";
    public static final String TAG_RELEASE = "RELEASE";

    private Topics() {
    }
}
