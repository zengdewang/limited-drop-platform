package com.limiteddrop.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    /** reserved; no tier logic in v1 */
    private String memberLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
