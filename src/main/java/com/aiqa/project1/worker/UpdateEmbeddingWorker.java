package com.aiqa.project1.worker;

import com.aiqa.project1.utils.MilvusSearchUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UpdateEmbeddingWorker {
    private final MilvusSearchUtils milvusSearchUtils;

    public UpdateEmbeddingWorker(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "update.embedding", durable = "true"),
            exchange = @Exchange(value = "update", type = ExchangeTypes.DIRECT),
            key = "update.embedding"
    ))
    public void run(Map<String, String> paramsMap) {
        try {
            String userId = paramsMap.get("userId");
            String documentName = paramsMap.get("documentName");
            if (milvusSearchUtils.deleteDocumentEmbeddingsByName(documentName, userId)) {
                return;
            } else {
                throw new RuntimeException("没找到用户%s的文件%s相关向量".formatted(userId, documentName));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
