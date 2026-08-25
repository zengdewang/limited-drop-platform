package com.limiteddrop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String brand;
    private String name;
    private String category;
    private String officialDoc;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
