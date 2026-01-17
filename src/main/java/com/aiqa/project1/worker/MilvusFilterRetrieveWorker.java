package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.*;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MilvusFilterRetrieveWorker extends AbstractRetrieveWorker {

    private final MilvusFilterRetriever milvusFilterRetriever;
    private final CacheAsideUtils cacheAsideUtils;

    private static final String KEYWORD_EXTRACTION_TEMPLATE_ChAT_HISTORY = """
            给定以下查询以及历史对话，你的任务是提取出该问题所在的文档名称,你要识别用户的意图，如果用户的查询有提及，暗示需要从历史信息中获取文档名称或者有这方面的意图，你需要从历史回答中提取出查询需要的文件
            <用户查询>
            %s
            </用户查询>
            
            <历史对话>
            %s
            </历史对话>
            
            至关重要的是，只需要输出文章名称，多个文章要使用英文逗号分开，除此之外不需要输出其他字符，其他内容一概不要！不要在关键词前后添加任何内容！
            <案例一>
            2106.09685v2.pdf
            </案例一>
            <案例二>
            2106.09685v2.pdf,2142.07865v2.pdf,2406.07788v3.pdf
            </案例二>
            现在，请输出你的答案：
            """;

    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询以及历史对话，你的任务是提取出该问题所在的文档名称
            <用户查询>
            %s
            </用户查询>
            至关重要的是，只需要输出文章名称，多个文章要使用英文逗号分开，除此之外不需要输出其他字符，其他内容一概不要！不要在关键词前后添加任何内容！
            <案例一>
            2106.09685v2.pdf
            </案例一>
            <案例二>
            2106.09685v2.pdf,2142.07865v2.pdf,2406.07788v3.pdf
            </案例二>
            现在，请输出你的答案：
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
            TimeoutControl timeoutControl, CacheAsideUtils cacheAsideUtils
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
        this.cacheAsideUtils = cacheAsideUtils;
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
    protected String extractKeywords(State state) {
        String query = (state.getRetrievalQuery() == null) ? state.getQuery() : state.getRetrievalQuery();
        String prompt;
        if (state.getHistoryChatRequirements()) {
            String chatMemory = String.join("\n", cacheAsideUtils.getChatMemory(state.getUserId(), state.getSessionId()));

            prompt = KEYWORD_EXTRACTION_TEMPLATE_ChAT_HISTORY.formatted(query, chatMemory);
        } else {
            prompt = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        }
        // 匹配"",""
        return "\"" + String.join("\",\"", douBaoLite.chat(prompt).split(",")) + "\"";
    }

    @Override
    protected List<Content> performRetrieve(Integer userId, Integer sessionId, String keywords, Query query) {
        return milvusFilterRetriever.retrieve(userId, sessionId, keywords, 10, query);
    }

}