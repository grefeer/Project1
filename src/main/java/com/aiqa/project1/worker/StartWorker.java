package com.aiqa.project1.worker;

import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.pojo.document.Document;
import com.aiqa.project1.pojo.qa.RetrievalDecision;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.query.Query;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StartWorker {
    private final RedisStoreUtils redisStoreUtils;
    private final DocumentMapper documentMapper;
    private final OpenAiChatModel douBaoLite;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String CHOOSE_TEMPLATE = """
                请完成以下任务：
                根据用户查询，判断回答用户问题的信息来源，如果提及相关文献的名称，则返回文档的名称。并识别用户的意图，如果需要上网查询，则告知网络查询。文档名称返回json格式
                首先，请明确用户的具体查询：
                <user_query>
                %s
                </user_query>
                
                
                需要上网查询的用户问题需要包含以下几个方面的其中一条：
                1. 时效性强：涉及最新事件（如“2025年奥运会举办城市”）、实时数据（股价、天气）、近期政策变动等，本地知识库未覆盖或已过期。
                2. 高度动态性：信息频繁变化（如商品价格、航班状态、社交媒体热点），难以在本地静态文档中维护。
                3. 长尾/冷门问题：极其专业或罕见的问题（如某小众开源库的 API 用法），本地知识库未收录。
                4. 事实验证需求：需要交叉验证多个权威来源（如新闻事件、科学发现），而本地仅有一家之言。
                5. 用户明确要求外部信息：用户提问包含“网上说…”、“最新报道”、“全球趋势”等暗示需外部数据的关键词。
                
                
                严格按照以下输出格式输出：
                {
                    "related_documents": ["第一份文档.pdf", "第二份文档.docx", ..., "第N份文档.md"]
                    "web_retrieval_flag": False/True
                }
                其中，"related_documents"包含需要检索的文档，"web_retrieval_flag"代表是否需要使用网络检索，True代表需要检索，False代表不需要检索。
                <案例>
                
                问题：fafafas.pdf文档和sfaff.docs文档分别讲了什么？
                响应：
                {
                    "related_documents": ["fafafas.pdf", "sfaff.docs"]
                    "web_retrieval_flag": False,
                    "local_retrieval_flag": True
                }
                
                问题：fafafas.pdf文档描述的地方现在气温多少度？
                响应：
                {
                    "related_documents": ["fafafas.pdf"]
                    "web_retrieval_flag": True,
                    "local_retrieval_flag": True
                }
                
                问题：沈阳今日气温多少度？
                响应：
                {
                    "related_documents": [],
                    "web_retrieval_flag": True,
                    "local_retrieval_flag": False
                }
                
                问题：请根据本地数据库回答，langchain是什么？
                响应：
                {
                    "related_documents": [],
                    "web_retrieval_flag": False,
                    "local_retrieval_flag": True
                }
                
                </案例>
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
        List<Document> documentList;

 //        queryWrapper.like("session_id", sessionId.toString());
        queryWrapper.apply("FIND_IN_SET({0}, session_id) > 0", sessionId);
        queryWrapper.eq("user_id", state.getUserId());
        documentList = documentMapper.selectList(queryWrapper);

        // 当前会话没有传递文件，用数据库进行回答
        if (documentList == null || documentList.isEmpty()) {
//            QueryWrapper<Document> allQueryWrapper = new QueryWrapper<>();
//            allQueryWrapper.eq("user_id", state.getUserId());
//            documentList = documentMapper.selectList(allQueryWrapper);
            state.setRetrievalGlobalFlag(true);
        }
//
//        String documentsAbstract = documentList.stream()
//                .map(document -> document.getDocumentName() + "的摘要:"+ document.getDescription().substring(0,100))
//                .collect(Collectors.joining("\n"));

        String prompt = CHOOSE_TEMPLATE.formatted(
//                documentsAbstract,
                (state.getRetrievalQuery() == null || state.getRetrievalQuery().isEmpty()) ? state.getQuery() : state.getRetrievalQuery()
        );

        String result = douBaoLite.chat(prompt);
        RetrievalDecision retrievalDecision = this.parseLLMJson(result);
        // 将相关文档以及检索来源写入state里
        if (retrievalDecision != null) {
            state.setRetrievalWebFlag(retrievalDecision.isWebRetrievalFlag());
            state.setLocalRetrievalFlag(retrievalDecision.isLocalRetrievalFlag());
            state.setRetrievalDocuments(retrievalDecision.getRelatedDocuments());
        }
    }

    public RetrievalDecision parseLLMJson(String llmOutput) {
        // 1. 清理LLM输出中可能的多余字符（比如前后的说明文字、空格）
        String cleanedJson = llmOutput.trim()
                // 移除JSON前后可能的非JSON字符（LLM可能多输出"答："等）
                .replaceAll("^[^\\{]*", "")
                .replaceAll("[^\\}]*$", "");

        // 2. 解析为目标Map
        try {
            return OBJECT_MAPPER.readValue(cleanedJson, RetrievalDecision.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response to JSON: " + e.getMessage(), e);
        }
    }
}

