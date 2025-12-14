package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.MilvusQueryRetriever;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Component
public class MilvusQueryRetrieveWorker {
    private final OpenAiChatModel douBaoLite;
    private final RabbitTemplate rabbitTemplate;

    private final MilvusQueryRetriever milvusQueryRetriever;
    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出三个最能代表该查询的关键词。
            用户查询：%s
            至关重要的是，关键词之间用英文逗号间隔开，并且你只需提供三个关键词，其他内容一概不要！不要在关键词前后添加任何内容！
            """;

    public MilvusQueryRetrieveWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, MilvusQueryRetriever milvusQueryRetriever) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.milvusQueryRetriever = milvusQueryRetriever;

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("MilvusQueryRetrieveWorker.retrieve"),
            exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
            key = "MilvusQueryRetrieveWorker.retrieve"
    ))
    public void run(State state) {

        Integer userId = state.getUserId();
        List<Content> retrievalInfo = state.getRetrievalInfo();
        String query = state.getRetrievalQuery();

        String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        String keywords = douBaoLite.chat(prompt1);

//        List<String> threadSafeKeywordsList = Collections.synchronizedList(new ArrayList<>()); 消息队列异步并行处理消息，无需synchronizedList
        List<String> KeywordsList = new ArrayList<>();
        Collections.addAll(KeywordsList, keywords.split(","));

        retrievalInfo.addAll(milvusQueryRetriever.retrieve(userId, KeywordsList, true, Query.from(query)));
        rabbitTemplate.convertAndSend("gather.topic", "MilvusQueryRetrieveWorker.retrieve", state);
    }
}
