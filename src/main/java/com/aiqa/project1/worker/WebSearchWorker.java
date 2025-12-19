package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 调用Tavily API实现
 */
@Component
public class WebSearchWorker {
    private final ContentRetriever webSearchContentRetriever;
    private final RedisStoreUtils redisStoreUtils;
    private final RabbitTemplate rabbitTemplate;

    public WebSearchWorker(ContentRetriever webSearchContentRetriever, RedisStoreUtils redisStoreUtils, RabbitTemplate rabbitTemplate) {
        this.webSearchContentRetriever = webSearchContentRetriever;
        this.redisStoreUtils = redisStoreUtils;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WebSearch.retrieve", durable = "true"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT), // 开始交换机，用于判断是需要用网络查询还是数据库查询
            key = "WebSearch.retrieve"
    ))
    public void run(State state) {
        redisStoreUtils.setRetrievalInfo(
                state.getUserId(),
                state.getSessionId(),
                state.getMemoryId(),
                "Retrieve",
                Collections.singleton(webSearchContentRetriever.retrieve(Query.from(state.getRetrievalQuery()))));
        rabbitTemplate.convertAndSend("gather.topic","WebSearch.retrieve", state);
    }
}
