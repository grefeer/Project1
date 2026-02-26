package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.DeadLetterMapper;
import com.aiqa.project1.pojo.DeadLetter;
import com.aiqa.project1.utils.MilvusSearchUtils1;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class UpdateEmbeddingConsumer {
    private final MilvusSearchUtils1 milvusSearchUtils1;
    private final DeadLetterMapper deadLetterMapper;

    public UpdateEmbeddingConsumer(MilvusSearchUtils1 milvusSearchUtils1, DeadLetterMapper deadLetterMapper) {
        this.milvusSearchUtils1 = milvusSearchUtils1;
        this.deadLetterMapper = deadLetterMapper;
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "update.embedding",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "50000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.update")
                    }
            ),
            exchange = @Exchange(value = "update", type = ExchangeTypes.DIRECT),
            key = "update.embedding"
    ))
    public void run(Map<String, String> paramsMap, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            String userId = paramsMap.get("userId");
            String documentName = paramsMap.get("documentName");
            if (milvusSearchUtils1.deleteDocumentEmbeddingsByName(documentName)) {
                return;
            } else {
                paramsMap.put("dead_message", "没找到用户%s的文件%s相关向量".formatted(userId, documentName));
                channel.basicAck(tag, false);
                throw new AmqpRejectAndDontRequeueException("没找到用户%s的文件%s相关向量".formatted(userId, documentName));
            }
        } catch (Exception e) {
            e.printStackTrace();
            paramsMap.put("dead_message", e.getMessage());
            channel.basicNack(tag, false, false);
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }

    /**
     * 定义一个死信消费者，专门处理失败的消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "dlq.update", durable = "true"),
            exchange = @Exchange(value = "dlx.exchange", type = ExchangeTypes.TOPIC),
            key = "dlx.update"
    ))
    public void handleDeadLetter(Map<String, String> failedData,
                                 @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 这里可以记录日志、存入数据库报错表、或者发送报警通知
        System.err.println("收到死信消息: " + failedData);

        if (xDeath != null && !xDeath.isEmpty()) {
            Map<String, Object> entry = xDeath.getFirst();
            System.out.println("--- 死信详情 ---");
            System.out.println("原因: " + entry.get("reason"));      // rejected, expired, maxlen
            System.out.println("原始队列: " + entry.get("queue"));   // 消息从哪个队列来的
            System.out.println("发生时间: " + entry.get("time"));

            DeadLetter entity = new DeadLetter();
            entity.setCreatedTime(LocalDateTime.now());
            entity.setMessage(failedData.toString() + "发生错误，原因：" + entry.get("reason"));
            entity.setErrorFiled("updateEmbedding");
            entity.setUserId(Integer.parseInt(failedData.get("user_id")));
            deadLetterMapper.insert(entity);
        }
    }
}