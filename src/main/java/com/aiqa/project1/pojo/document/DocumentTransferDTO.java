package com.aiqa.project1.pojo.document;

import lombok.Data;
import java.io.Serializable;

/**
 * 文档传输DTO，用于RabbitMQ消息传递
 */
@Data
public class DocumentTransferDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 文件名称
    private String fileName;
    // 文件内容字节数组
    private byte[] fileBytes;
    // 用户ID
    private String userId;
    // 会话ID
    private String sessionId;
    private String documentId;

    private String tagName;


}