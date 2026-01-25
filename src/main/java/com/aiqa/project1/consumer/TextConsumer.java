package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.pojo.document.DocumentTransferDTO;
import com.aiqa.project1.utils.DataProcessUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
@Slf4j
public class TextConsumer {
    private final DataProcessUtils dataProcessUtils;
    private final DocumentMapper documentMapper;


    public TextConsumer(DataProcessUtils dataProcessUtils, DocumentMapper documentMapper) {
        this.dataProcessUtils = dataProcessUtils;
        this.documentMapper = documentMapper;
    }

    // 删除原有的接收MultipartFile/InputStream的方法，新增接收DTO的方法
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "TextProcess", durable = "true"),
            exchange = @Exchange(value = "TextProcess", type = ExchangeTypes.DIRECT),
            key = "text.divide"
    ))
    public void processDocument(DocumentTransferDTO dto) {
        try {
            // 将DTO转换为InputStream（供原有逻辑使用）
            InputStream inputStream = new ByteArrayInputStream(dto.getFileBytes());
            // 调用原有处理逻辑
            String abstractText = dataProcessUtils.processDocument(
                    dto.getUserId(),
                    Integer.valueOf(dto.getSessionId()),
                    inputStream,
                    dto.getFileName()
            );
            LambdaUpdateWrapper<Document> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper
                    // 只更新摘要字段（description），最小化更新范围
                    .set(Document::getDescription, abstractText)
                    // 核心条件：匹配目标文档（document_id有唯一索引，更新极快）
                    .eq(Document::getDocumentId, dto.getDocumentId())
                    // 防护：仅更新可用状态的文档，避免误更已删除文档
                    .eq(Document::getStatus, "AVAILABLE");

            // 执行更新（返回受影响行数，可根据需要判断是否更新成功）
            int updateCount = documentMapper.update(null, updateWrapper);
            if (updateCount == 0) {
                log.warn("更新文档摘要失败，未找到匹配的文档！documentId:{}", dto.getDocumentId());
            } else {
                log.info("文档摘要更新成功！documentId:{}", dto.getDocumentId());
            }

        } catch (Exception e) {
            e.printStackTrace();
            // 异常处理逻辑（如日志记录、重试等）
        }
    }

}
