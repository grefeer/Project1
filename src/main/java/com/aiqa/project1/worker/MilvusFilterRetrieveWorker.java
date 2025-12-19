package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.MilvusFilterContentRetriever;
import com.aiqa.project1.utils.MilvusFilterRetriever;
import com.aiqa.project1.utils.MilvusQueryContentRetriever;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.alibaba.fastjson.JSON;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Component
public class MilvusFilterRetrieveWorker {
    private final OpenAiChatModel douBaoLite;
    private final MilvusFilterRetriever milvusFilterRetriever;
    private final RabbitTemplate rabbitTemplate;

    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出该问题所在的文档名称。
            用户查询：%s
            至关重要的是，只需要输出文章名称，不需要输出其他字符，其他内容一概不要！不要在关键词前后添加任何内容！
            例如：
            2106.09685v2.pdf
            """;
    private final RedisStoreUtils redisStoreUtils;

    public MilvusFilterRetrieveWorker(OpenAiChatModel douBaoLite, MilvusFilterRetriever milvusFilterRetriever, MilvusQueryContentRetriever milvusQueryContentRetriever, MilvusFilterContentRetriever milvusFilterContentRetriever, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.milvusFilterRetriever = milvusFilterRetriever;
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "MilvusFilterRetrieveWorker.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
            key = "MilvusFilterRetrieveWorker.retrieve"
    ))
    public void run(State state) {

        try {
            Integer userId = state.getUserId();

            String query = state.getRetrievalQuery();

            String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
            String keywords = douBaoLite.chat(prompt1);
            List<Content> retrievalInformation = milvusFilterRetriever.retrieve(userId, keywords, 5, Query.from(query));

            redisStoreUtils.setRetrievalInfo(
                    userId,
                    state.getSessionId(),
                    state.getMemoryId(),
                    "Retrieve",
                    retrievalInformation
                            .stream()
                            .map(content -> "<MilvusFilterRetrieveWorker检索结果>" + content.toString() + "</MilvusFilterRetrieveWorker检索结果>")
                            .collect(Collectors.toList())
            );

            rabbitTemplate.convertAndSend("gather.topic", "MilvusFilterRetrieveWorker.retrieve", state);
        } catch (AmqpException e) {
            e.printStackTrace();
            // 出现错误，直接返回
            try {
                rabbitTemplate.convertAndSend("gather.topic", "MilvusFilterRetrieveWorker.retrieve", state);
            } catch (AmqpException ex) {
                throw new RuntimeException("rabbitmq异常，具体发生在MilvusFilterRetrieveWorker向gather.topic发送消息的过程中",ex);
            }
        }

    }
}
