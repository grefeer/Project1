package com.aiqa.project1.pojo.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

// 文档主表实体
@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String documentId; // 文档唯一标识（如雪花ID）
    private String userId;
    private String documentName;
    private String description;
    private String fileType;
    private Long currentVersion;
    private Long fileSize;
    private String status; // AVAILABLE/DELETED
    private LocalDateTime uploadTime;
    private LocalDateTime updateTime;
}

