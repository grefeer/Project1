package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.MilvusHybridRetriever;
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
public class MilvusHybridRetrieveWorker {
    private final OpenAiChatModel douBaoLite;
    private final RabbitTemplate rabbitTemplate;

    private final MilvusHybridRetriever milvusHybridRetriever;
    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出三个最能代表该查询的关键词。
            用户查询：%s
            至关重要的是，关键词之间用英文逗号间隔开，并且你只需提供三个关键词，其他内容一概不要！不要在关键词前后添加任何内容！
            """;
    private final RedisStoreUtils redisStoreUtils;

    public MilvusHybridRetrieveWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, MilvusHybridRetriever milvusHybridRetriever, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.milvusHybridRetriever = milvusHybridRetriever;
        this.redisStoreUtils = redisStoreUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "MilvusHybridRetrieveWorker.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
            key = "MilvusHybridRetrieveWorker.retrieve"
    ))
    public void run(State state) {

        try {
            Integer userId = state.getUserId();

            String query = state.getRetrievalQuery();

            String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
            String keywords = douBaoLite.chat(prompt1);

            // 每次将文件传入数据库时都先根据文件提取出文件是属于哪个领域的，这样检索的时候就减少了搜索范围
            // 用户的问题中如果有提及文件的，直接调用milvusFilterContentRetriever，
            List<Content> retrievalInformation = milvusHybridRetriever.retrieveTopK5WithRRF(userId, keywords, Query.from(query));
            System.out.println(retrievalInformation.size());
            retrievalInformation
                    .stream()
                    .map(Object::toString).forEach(s -> System.out.println("aiai:\n" +s));

            redisStoreUtils.setRetrievalInfo(
                    userId,
                    state.getSessionId(),
                    state.getMemoryId(),
                    "Retrieve",
                    retrievalInformation
                            .stream()
                            .map(content -> "<MilvusHybridRetrieveWorker检索结果>" + content.toString() + "</MilvusHybridRetrieveWorker检索结果>")
                            .collect(Collectors.toList())
            );

            rabbitTemplate.convertAndSend("gather.topic", "MilvusHybridRetrieveWorker.retrieve", state);
        } catch (AmqpException e) {
            e.printStackTrace();
            // 出现错误，直接返回
            try {
                rabbitTemplate.convertAndSend("gather.topic", "MilvusHybridRetrieveWorker.retrieve", state);
            } catch (AmqpException ex) {
                throw new RuntimeException("rabbitmq异常，具体发生在MilvusHybridRetrieveWorker向gather.topic发送消息的过程中",ex);
            }
        }
    }
}
