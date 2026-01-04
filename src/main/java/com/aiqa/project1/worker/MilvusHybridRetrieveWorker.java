package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.AsyncTaskExecutor;
import com.aiqa.project1.utils.RateLimiter;
import com.aiqa.project1.utils.TimeoutControl;
import com.aiqa.project1.utils.MilvusHybridRetriever;
import com.aiqa.project1.utils.MilvusSearchUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Slf4j
public class MilvusHybridRetrieveWorker extends AbstractRetrieveWorker  {

    private final MilvusHybridRetriever milvusHybridRetriever;

    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出三个最能代表该查询的关键词。
            用户查询：%s
            至关重要的是，关键词之间用英文逗号间隔开，并且你只需提供三个关键词，其他内容一概不要！不要在关键词前后添加任何内容！
            """;

    public MilvusHybridRetrieveWorker(
        OpenAiChatModel douBaoLite,
        MilvusHybridRetriever milvusHybridRetriever,
        RabbitTemplate rabbitTemplate,
        RedisTemplate<String, Object> redisTemplate,
        ObjectMapper objectMapper,
        MilvusSearchUtils milvusSearchUtils,
        RedisStoreUtils redisStoreUtils,
        AsyncTaskExecutor asyncTaskExecutor,
        RateLimiter rateLimiter,
        TimeoutControl timeoutControl
    ) {
        super(
            rabbitTemplate,
            redisTemplate,
            douBaoLite,
            objectMapper,
            redisStoreUtils,
            asyncTaskExecutor,
            timeoutControl,
            rateLimiter,
            milvusSearchUtils);
        this.milvusHybridRetriever = milvusHybridRetriever;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "MilvusHybridRetrieveWorker.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
            key = "MilvusHybridRetrieveWorker.retrieve"
    ))
    public void run(State state) {
        executeRetrieve(state, "MilvusHybridRetrieveWorker.retrieve");
    }

    @Override
    protected List<Content> performRetrieve(Integer userId, Integer sessionId, String keywords, Query query) {
        return milvusHybridRetriever.retrieveTopK10WithRRF(userId, sessionId, keywords, query);
    }

    @Override
    protected String extractKeywords(String query) {
        String prompt = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        return douBaoLite.chat(prompt);
    }

    
}
