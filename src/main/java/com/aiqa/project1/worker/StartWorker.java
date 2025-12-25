package com.aiqa.project1.worker;

import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StartWorker {
    private final RedisStoreUtils redisStoreUtils;
    private final DocumentMapper documentMapper;
    private final OpenAiChatModel douBaoLite;

    private final String CHOOSE_TEMPLATE = """
                你是多智能体系统的选择节点，核心任务是根据用户查询和数据库中文档的描述，判断回答用户问题的信息来源：是利用数据库现有内容，还是需要通过网络查找新信息。
                
                首先，请仔细阅读数据库中文档的描述：
                <database_docs>
                %s
                </database_docs>
                
                接下来，请明确用户的具体查询：
                <user_query>
                %s
                </user_query>
                
                判断标准如下：
                - 若数据库文档描述中明确包含与用户查询直接相关的主题、领域或核心信息，则判定为“利用数据库回答”
                - 若数据库文档描述未覆盖用户查询的核心需求，或需要实时、动态、数据库未收录的信息，则判定为“网络查找相关信息回答”
                
                请直接在<判断结果>标签中给出最终决策，无需输出思考过程。判断结果只能是“利用数据库回答”或“网络查找相关信息回答”。
                """;
    private final RabbitTemplate rabbitTemplate;

    public StartWorker(RedisStoreUtils redisStoreUtils, DocumentMapper documentMapper, OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate) {
        this.redisStoreUtils = redisStoreUtils;
        this.documentMapper = documentMapper;
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "start", durable = "true"),
            exchange = @Exchange(value = "Start", type = ExchangeTypes.DIRECT), // 开始交换机，用于判断是需要用网络查询还是数据库查询
            key = "start"
    ))
    public void run(State state) {
        Integer sessionId = state.getSessionId();
        QueryWrapper<Document> queryWrapper = new QueryWrapper<>();
        List<Document> documentList = new ArrayList<>();

//        queryWrapper.like("session_id", sessionId.toString());
        queryWrapper.apply("FIND_IN_SET({0}, session_id) > 0", sessionId);
        queryWrapper.eq("user_id", state.getUserId());
        documentList = documentMapper.selectList(queryWrapper);

        // 当前会话没有传递文件，用数据库进行回答
        if (documentList.isEmpty()) {
            QueryWrapper<Document> allQueryWrapper = new QueryWrapper<>();
            allQueryWrapper.eq("user_id", state.getUserId());
            documentList = documentMapper.selectList(allQueryWrapper);
            state.setRetrievalGlobalFlag(true);
        }

        String documentsAbstract = documentList.stream()
                .map(document -> document.getDocumentName() + "的摘要:"+ document.getDescription())
                .collect(Collectors.joining("\n"));

        String prompt = CHOOSE_TEMPLATE.formatted(
                documentsAbstract,
                (state.getRetrievalQuery() == null || state.getRetrievalQuery().isEmpty()) ? state.getQuery() : state.getRetrievalQuery()
        );

        String result = douBaoLite.chat(prompt);
        if (result.contains("数据库")) {
            rabbitTemplate.convertAndSend("refection.direct", "new.problem", state);
        } else {
            state.setRetrievalDBFlag(false);
            redisStoreUtils.putRetrievalCount(state.getUserId(), sessionId , state.getMemoryId(), 1);
            rabbitTemplate.convertAndSend("Retrieve", "WebSearch.retrieve", state);
        }

    }

}

