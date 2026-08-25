package com.limiteddrop.qa.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String sourceId;
    private Long productId;
    private Integer chunkIndex;
    private String content;
    private Long milvusId;
    private String status;
    private LocalDateTime createdAt;
}
