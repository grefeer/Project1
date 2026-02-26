package com.aiqa.project1.pojo.nodes;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.service.impl.SpecialTagService;
import com.aiqa.project1.utils.MilvusSearchUtils;
import com.aiqa.project1.utils.MilvusSearchUtils1;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.aiqa.project1.utils.RegexValidate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.milvus.v2.service.vector.response.SearchResp;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class NaiveRAGGraph {
    private final OpenAiChatModel douBaoLite;
    private final MilvusSearchUtils1 milvusSearchUtils1;
    private final RedisStoreUtils redisStoreUtils;
    private final RabbitTemplate rabbitTemplate;
    private final SpecialTagService specialTagService;

    public NaiveRAGGraph(OpenAiChatModel douBaoLite, MilvusSearchUtils1 milvusSearchUtils1, RedisStoreUtils redisStoreUtils, RabbitTemplate rabbitTemplate, SpecialTagService specialTagService) {
        this.douBaoLite = douBaoLite;
        this.milvusSearchUtils1 = milvusSearchUtils1;
        this.redisStoreUtils = redisStoreUtils;
        this.rabbitTemplate = rabbitTemplate;
        this.specialTagService = specialTagService;
    }

    public CompiledGraph<NaiveRAGState> buildGraph() throws GraphStateException {
        // Define node's actions


        AsyncNodeAction<NaiveRAGState> retrievalAction = node_async(state -> {

            try {
                String query = state.getQuery();
                List<String> documentName = RegexValidate.extractContentInBookMark(query);
                String filterExpr = null;
                if (documentName != null && !documentName.isEmpty()) {
                    StringBuilder valuesStr = new StringBuilder();
                    for (int i = 0; i < documentName.size(); i++) {
                        valuesStr.append("\"").append(documentName.get(i)).append("\"");
                        if (i != documentName.size() - 1) {
                            valuesStr.append(", ");
                        }
                    }
                    filterExpr = String.format("come_from in [%s]", valuesStr);
                }
                List<OrganizationTag> tagList = specialTagService.getAllTagsByUserId_(state.getUserId());

                List<String> partitionNames = new ArrayList<>(tagList.stream()
                        .map(OrganizationTag::getTagName)
                        .toList());
                // 去除个人标签
                partitionNames.remove("PERSONAL");
                
                SearchResp searchResp = milvusSearchUtils1.hybridSearch(
                        query,
                        query,
                        state.getUserId(),
                        partitionNames,
                        7, 60, filterExpr
                );
                List<String> contentList = new ArrayList<>(MilvusSearchUtils.getContentsFromSearchResp(searchResp)
                        .stream().map(Objects::toString).toList());
                return Map.of("retrieval_info", contentList);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });


        AsyncNodeAction<NaiveRAGState> answerAction = node_async(state -> {
            String chatHistory = redisStoreUtils.getChatMemory(
                            state.getUserId(),
                            state.getSessionId(),
                            SystemConfig.MAX_REWRITE_HISTORY_SIZE).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));

            String prompt = ANSWER_TEMPLATE.formatted(chatHistory, state.getQuery(), state.getRetrievalInfo().toString());
            String finalAnswer = douBaoLite.chat(prompt);

            redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), "<最终回答>" + finalAnswer);
            rabbitTemplate.convertAndSend(
                    "mysql.update",
                    "chat.memory",
                    new State(
                            state.getUserId(),
                            state.getSessionId(),
                            state.getMemoryId(),
                            state.getQuery()
                    ));

            return Map.of("answer", finalAnswer);
        });
        
        // Define the graph
        StateGraph<NaiveRAGState> graph = new StateGraph<>(NaiveRAGState.SCHEMA, NaiveRAGState::new)
                .addNode("retrieval", retrievalAction)
                .addNode("answer", answerAction)
                .addEdge(START, "retrieval")
                .addEdge("retrieval", "answer")
                .addEdge("answer", END);

        // Compile the graph
        CompileConfig compileConfig = CompileConfig.builder()
//                .checkpointSaver(new MemorySaver())
                .build();
        
        return graph.compile(compileConfig);
    }

    private static final String ANSWER_TEMPLATE = """
    你是多智能体系统的问答节点，核心任务是结合历史对话上下文，准确全面回答用户当前问题。
    
    首先，阅读以下历史对话，理解背景及用户此前交互：
    <历史对话> %s </历史对话>
    当前用户问题：
    <用户查询> %s </用户查询>
    检索到的信息：
    <检索信息> %s </检索信息>
    回答规则：
    1. 结合历史上下文逻辑，确保回答与之前交互连贯一致，回答完整有逻辑，保证信息完整准确
    2. 回答内容需基于历史信息，禁止编造未提及内容，若无直接相关信息，明确告知无法回答
    3. 问题和回答的语言要一致，这是十分重要的
    4. 要根据历史对话回答问题，而不是直接把历史对话输出出来，回答里不要出现类似"子问题"，"子问题回答"等内容
    5. 如果用户要求"介绍论文"，回答必须包含论文的核心问题、提出的方法、关键创新点和主要实验结果
    现在，请开始回答。
    """;
}