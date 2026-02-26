package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.DeadLetterMapper;
import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.pojo.DeadLetter;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.pojo.document.DocumentTransferDTO;
import com.aiqa.project1.utils.DataProcessUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.aiqa.project1.utils.SseEmitterManager;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TextConsumer {
    private final DataProcessUtils dataProcessUtils;
    private final DocumentMapper documentMapper;
    private final RedisStoreUtils redisStoreUtils;
    private final SseEmitterManager sseEmitterManager;
    private final DeadLetterMapper deadLetterMapper;


    public TextConsumer(DataProcessUtils dataProcessUtils, DocumentMapper documentMapper, RedisStoreUtils redisStoreUtils, SseEmitterManager sseEmitterManager, DeadLetterMapper deadLetterMapper) {
        this.dataProcessUtils = dataProcessUtils;
        this.documentMapper = documentMapper;
        this.redisStoreUtils = redisStoreUtils;
        this.sseEmitterManager = sseEmitterManager;
        this.deadLetterMapper = deadLetterMapper;
    }

    // 删除原有的接收MultipartFile/InputStream的方法，新增接收DTO的方法
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "TextProcess",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "100000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.text")
                    }
            ),
            exchange = @Exchange(value = "TextProcess", type = ExchangeTypes.DIRECT),
            key = "text.divide"
    ))
    public void processDocument(DocumentTransferDTO dto, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // 将DTO转换为InputStream（供原有逻辑使用）
            InputStream inputStream = new ByteArrayInputStream(dto.getFileBytes());
            // 调用原有处理逻辑
            String abstractText = dataProcessUtils.processDocument(
                    dto.getUserId(),
                    inputStream,
                    dto.getFileName(),
                    dto.getTagName()
            );
            LambdaUpdateWrapper<Document> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper
                    // 只更新摘要字段（description）和状态，最小化更新范围
                    .set(Document::getDescription, abstractText)
                    .set(Document::getStatus, "AVAILABLE")
                    // 核心条件：匹配目标文档（document_id有唯一索引，更新极快）
                    .eq(Document::getDocumentId, dto.getDocumentId());
//                    // 防护：仅更新可用状态的文档，避免误更已删除文档
//                    .eq(Document::getStatus, "NOT_EMBEDDED");

            // 执行更新（返回受影响行数，可根据需要判断是否更新成功）
            int updateCount = documentMapper.update(updateWrapper);
            if (updateCount == 0) {
                log.warn("更新文档摘要失败，未找到匹配的文档！documentId:{}", dto.getDocumentId());
            } else {
                log.info("文档摘要更新成功！documentId:{}", dto.getDocumentId());

                // 2. 构造简洁的状态对象（推荐：只推送前端需要的字段，减少数据传输）
                Map<String, String> statusVO = new HashMap<>();
                statusVO.put("documentId", dto.getDocumentId());
                statusVO.put("status", "AVAILABLE");

                // 3. 通过SSE推送状态到前端（客户端标识用documentId，前端监听该ID的连接）
                try {
                    // 根据userId
                    log.info("准备推送SSE - userId: {}, documentId: {}", dto.getUserId(), dto.getDocumentId());
                    sseEmitterManager.sendDocumentStatus(dto.getUserId(), statusVO);
                    log.info("SSE推送文档状态成功！documentId:{}", dto.getDocumentId());
                    channel.basicAck(tag, false);
                } catch (Exception e) {
                    // 捕获推送异常，不影响主业务流程
                    log.error("SSE推送文档状态失败！documentId:{}，异常信息：{}", dto.getDocumentId(), e.getMessage());
                    channel.basicNack(tag, false, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 使用TagName传递错误信息
            dto.setTagName(e.getMessage());
            channel.basicNack(tag, false, false);
//            throw new AmqpRejectAndDontRequeueException(e);
        }
    }

    /**
     * 定义一个死信消费者，专门处理失败的消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "dlq.text", durable = "true"),
            exchange = @Exchange(value = "dlx.exchange", type = ExchangeTypes.TOPIC),
            key = "dlx.text"
    ))
    public void handleDeadLetter(DocumentTransferDTO dto,
                                 @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 这里可以记录日志、存入数据库报错表、或者发送报警通知
        System.err.println("收到死信消息: " + dto);

        if (xDeath != null && !xDeath.isEmpty()) {
            Map<String, Object> entry = xDeath.getFirst();
            System.out.println("--- 死信详情 ---");
            System.out.println("原因: " + entry.get("reason"));      // rejected, expired, maxlen
            System.out.println("原始队列: " + entry.get("queue"));   // 消息从哪个队列来的
            System.out.println("发生时间: " + entry.get("time"));
            DeadLetter entity = new DeadLetter();
            entity.setCreatedTime(LocalDateTime.now());
            entity.setMessage(dto.toString() + "发生错误，原因：" + entry.get("reason"));

            entity.setErrorFiled("text");
            entity.setUserId(Integer.parseInt(dto.getUserId()));
            deadLetterMapper.insert(entity);
        }

    }

}
