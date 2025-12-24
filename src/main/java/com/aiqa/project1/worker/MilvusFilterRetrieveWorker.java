package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.util.AsyncTaskExecutor;
import com.aiqa.project1.util.RateLimiter;
import com.aiqa.project1.util.RedisPoolManager;
import com.aiqa.project1.util.TimeoutControl;
import com.aiqa.project1.utils.MilvusFilterRetriever;
import com.aiqa.project1.utils.MilvusSearchUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
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
public class MilvusFilterRetrieveWorker extends AbstractRetrieveWorker {
    
    private final MilvusFilterRetriever milvusFilterRetriever;

    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出该问题所在的文档名称。
            用户查询：%s
            至关重要的是，只需要输出文章名称，不需要输出其他字符，其他内容一概不要！不要在关键词前后添加任何内容！
            例如：
            2106.09685v2.pdf
            """;

    public MilvusFilterRetrieveWorker(
        OpenAiChatModel douBaoLite,
        MilvusFilterRetriever milvusFilterRetriever,
        RabbitTemplate rabbitTemplate,
        RedisStoreUtils redisStoreUtils,
        RedisTemplate<String, Object> redisTemplate,
        ObjectMapper objectMapper,
        MilvusSearchUtils milvusSearchUtils,
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
        this.milvusFilterRetriever = milvusFilterRetriever;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "MilvusFilterRetrieveWorker.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
            key = "MilvusFilterRetrieveWorker.retrieve"
    ))
    public void run(State state) {
        executeRetrieve(state, "MilvusFilterRetrieveWorker.retrieve");
    }

    @Override
    protected String extractKeywords(String query) {
        String prompt = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        return douBaoLite.chat(prompt);
    }

    @Override
    protected List<Content> performRetrieve(Integer userId, Integer sessionId, String keywords, Query query) {
        return milvusFilterRetriever.retrieve(userId, sessionId, keywords, 5, query);
    }

    @Override
    protected List<Content> parseSearchResults(Object searchResults) {
        try {
            return MilvusSearchUtils.getContentsFromSearchResp((SearchResp) searchResults);
        } catch (Exception e) {
            log.error("解析Milvus搜索结果失败", e);
            return List.of();
        }
    }
}
