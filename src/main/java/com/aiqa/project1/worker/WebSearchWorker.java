package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.AsyncTaskExecutor;
import com.aiqa.project1.utils.RateLimiter;
import com.aiqa.project1.utils.TimeoutControl;
import com.aiqa.project1.utils.MilvusSearchUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 调用Tavily API实现
 */
@Component
@Slf4j
public class WebSearchWorker extends AbstractRetrieveWorker {
    private final ContentRetriever webSearchContentRetriever;

    public WebSearchWorker(
            OpenAiChatModel douBaoLite,
            ContentRetriever webSearchContentRetriever,
            RabbitTemplate rabbitTemplate,
            RedisStoreUtils redisStoreUtils,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            MilvusSearchUtils milvusSearchUtils,
            AsyncTaskExecutor asyncTaskExecutor,
            RateLimiter rateLimiter,
            TimeoutControl timeoutControl, RateLimiter rateLimiter1
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
        this.webSearchContentRetriever = webSearchContentRetriever;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WebSearch.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT), // 开始交换机，用于判断是需要用网络查询还是数据库查询
            key = "WebSearch.retrieve"
    ))
    public void run(State state) {
        executeRetrieve(state, "WebSearch.retrieve");
    }

    @Override
    protected List<Content> performRetrieve(Integer userId, Integer sessionId, String keywords, Query query) {
        try {
            return webSearchContentRetriever.retrieve(query);
        } catch (Exception e) {
            log.error("用户[{}]会话[{}]网页检索失败，query:{}", userId, sessionId, query, e);
            return Collections.emptyList();
        }
    }

    @Override
    protected String extractKeywords(State state) {
        return "";
    }

}
