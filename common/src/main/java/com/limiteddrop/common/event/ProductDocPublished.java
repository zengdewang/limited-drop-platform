package com.limiteddrop.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * product → qa：商品官方介绍发布/更新，qa 重建该商品的官方文档切片。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDocPublished {
    private Long productId;
    private String brand;
    private String name;
    private String category;
    private String officialDoc;
}
