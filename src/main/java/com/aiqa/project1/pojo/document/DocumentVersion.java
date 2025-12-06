package com.aiqa.project1.pojo.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// 文档版本表实体
@Data
@TableName("document_version")
public class DocumentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String documentId;
    private Long version;
    private String ossPath;
    private Long fileSize;
    private String previewUrl;
    private LocalDateTime uploadTime;
}
