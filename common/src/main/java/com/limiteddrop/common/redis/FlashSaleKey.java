package com.limiteddrop.common.redis;

/**
 * 秒杀 Redis key 构造。见 ADR-0001 三层削峰。
 */
public final class FlashSaleKey {

    /** 剩余库存（string int，预热） */
    public static String inv(long dropId) {
        return "fs:drop:" + dropId + ":inv";
    }

    /** 已抢顾客集合（set，幂等） */
    public static String users(long dropId) {
        return "fs:drop:" + dropId + ":users";
    }

    /** 开售标记（string，存在即开售，TTL=发售时长） */
    public static String open(long dropId) {
        return "fs:drop:" + dropId + ":open";
    }

    /** 该顾客的 orderNo（string） */
    public static String order(long dropId, long customerId) {
        return "fs:drop:" + dropId + ":order:" + customerId;
    }

    /** 清场时删除所有 order 键 */
    public static String orderPattern(long dropId) {
        return "fs:drop:" + dropId + ":order:*";
    }

    private FlashSaleKey() {
    }
}
