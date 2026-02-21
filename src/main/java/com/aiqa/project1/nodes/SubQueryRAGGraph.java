package com.aiqa.project1.nodes;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.service.impl.SpecialTagService;
import com.aiqa.project1.utils.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.milvus.v2.service.vector.response.SearchResp;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class SubQueryRAGGraph {
    private final OpenAiChatModel douBaoLite;
    private final MilvusSearchUtils1 milvusSearchUtils1;
    private final QuerySplitter querySplitter;
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final RedisStoreUtils redisStoreUtils;
    private final RabbitTemplate rabbitTemplate;
    private final SpecialTagService specialTagService;

    String subQueryTemplate = """
                以下是子查询的内容：
                %s
                
                以下是检索到的相关信息：
                %s
                
                请根据检索信息回答查询，现在请生成回答：
                """;


    public SubQueryRAGGraph(OpenAiChatModel douBaoLite, MilvusSearchUtils1 milvusSearchUtils1, QuerySplitter querySplitter, AsyncTaskExecutor asyncTaskExecutor, RedisStoreUtils redisStoreUtils, RabbitTemplate rabbitTemplate, SpecialTagService specialTagService) {
        this.douBaoLite = douBaoLite;
        this.milvusSearchUtils1 = milvusSearchUtils1;
        this.querySplitter = querySplitter;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.redisStoreUtils = redisStoreUtils;
        this.rabbitTemplate = rabbitTemplate;
        this.specialTagService = specialTagService;
    }


    public CompiledGraph<SubQueryRAGState> buildGraph() throws GraphStateException {
        // Define node's actions


        AsyncNodeAction<SubQueryRAGState> retrievalAction = node_async(state -> {
            List<SubQuery1> subQuery = state.getSubQuery();
            List<Map<String,List<String>>> retrievalInfo = new ArrayList<>();

            String query = state.getQuery();
            List<String> documentName = RegexValidate.extractContentInBookMark(query);
            String filterExpr;
            if (documentName != null && !documentName.isEmpty()) {
                StringBuilder valuesStr = new StringBuilder();
                for (int i = 0; i < documentName.size(); i++) {
                    valuesStr.append("\"").append(documentName.get(i)).append("\"");
                    if (i != documentName.size() - 1) {
                        valuesStr.append(", ");
                    }
                }
                filterExpr = String.format("come_from in [%s]", valuesStr);
            } else {
                filterExpr = null;
            }
            System.out.println(filterExpr);
            try {
                List<OrganizationTag> tagList = specialTagService.getAllTagsByUserId_(state.getUserId());

                List<String> partitionNames = new ArrayList<>(tagList.stream()
                        .map(OrganizationTag::getTagName)
                        .toList());
                // 去除个人标签
                partitionNames.remove("PERSONAL");

                subQuery.forEach(subQuery1 ->{
                    // 从所在组织的文档中检索,如果标签包含ADMIN，全文档检索
                    SearchResp searchResp = milvusSearchUtils1.hybridSearch(
                            subQuery1.getSub_question(),
                            subQuery1.getSub_question(),
                            state.getUserId(),
                            partitionNames,
                            10, 60, filterExpr
                    );
                    List<String> contentList = new ArrayList<>(MilvusSearchUtils.getContentsFromSearchResp(searchResp)
                            .stream().map(Objects::toString).toList());
                    retrievalInfo.add(Map.of(subQuery1.getSub_question(), contentList));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            return Map.of("retrieval_info", retrievalInfo);
        });

        AsyncNodeAction<SubQueryRAGState> querySplitAction = node_async(state -> {
            try {
                List<SubQuery1> subQueries = querySplitter.splitQuery(state.getQuery(), state.getToolMetadataList());
                redisStoreUtils.setChatMemory(
                        state.getUserId(),
                        state.getSessionId(),
                        "<AI思考>原问题经AI拆解为以下子问题：\n" + subQueries.stream().map(subQuery1 -> "    " + subQuery1.getSub_question()).collect(Collectors.joining("\n"))
                );
                return Map.of("sub_query", subQueries);
            } catch (Exception e) {
                e.printStackTrace();
                return Map.of();
            }
        });

        AsyncNodeAction<SubQueryRAGState> answerAction = node_async(state -> {
            List<Map<String, List<String>>> childQuery = state.getRetrievalInfo();
            List<SubQuery1> subQuery1 = state.getSubQuery();
            List<Supplier<SubQuery1>> task_list = new ArrayList<>();
            try {
                for (int i = 0; i < childQuery.size(); i++) {
                    Map<String, List<String>> retrievalInfo = childQuery.get(i);
                    SubQuery1 subQuery = subQuery1.get(i);
                    List<String> contentList = retrievalInfo.get(subQuery.getSub_question());

                    Supplier<SubQuery1> subQuerySupplier = () -> {
                        String chat = douBaoLite.chat(subQueryTemplate.formatted(subQuery, contentList.toString()));
                        subQuery.setSub_answer(chat);
                        return subQuery;
                    };
                    task_list.add(subQuerySupplier);
                }
                List<SubQuery1> resultFuture = asyncTaskExecutor.submitAllWithResult(task_list).get();
                String finalAnswer = generateFinalAnswer(state, resultFuture);
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return Map.of();
        });
        
        // Define the graph
        StateGraph<SubQueryRAGState> graph = new StateGraph<>(SubQueryRAGState.SCHEMA, SubQueryRAGState::new)
                .addNode("querySplit", querySplitAction)
                .addNode("retrieval", retrievalAction)
                .addNode("answer", answerAction)
                .addEdge(START, "querySplit")
                .addEdge("querySplit", "retrieval")
                .addEdge("retrieval", "answer")
                .addEdge("answer", END);

        // Compile the graph
        CompileConfig compileConfig = CompileConfig.builder()
//                .checkpointSaver(new MemorySaver())
                .build();
        
        return graph.compile(compileConfig);
    }


    /**
     * 生成最终答案
     */
    private String generateFinalAnswer(SubQueryRAGState originalQuery, List<SubQuery1> processedQueries) {
        StringBuilder contextBuilder = new StringBuilder();

        for (SubQuery1 sq : processedQueries) {
            contextBuilder.append("--- 子问题 ---\n")
                    .append(sq.getSub_question()).append("\n")
                    .append("--- 调研结果 ---\n")
                    .append(sq.getSub_answer()).append("\n\n");
        }

        String chatHistory = redisStoreUtils.getChatMemory(
                        originalQuery.getUserId(),
                        originalQuery.getSessionId(),
                        SystemConfig.MAX_REWRITE_HISTORY_SIZE).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        String prompt = FINAL_ANSWER_TEMPLATE.formatted(originalQuery.getQuery(), contextBuilder.toString(), chatHistory);
        return douBaoLite.chat(prompt);
    }


    private static final String FINAL_ANSWER_TEMPLATE = """
            用户提出了一个复杂问题：
            %s
            
            为了回答这个问题，我们将其拆解为以下子问题并分别进行了调研：
            %s
            
            之前的对话如下所示：
            %s
            
            请综合上述子问题的调研结果，生成用户原始问题的最终完整答案。
            要求：
            1. 逻辑通顺，结构清晰。
            2. 如果子问题由于缺乏信息未能回答，请在最终答案中如实说明缺失的部分。
            3. 不要暴露内部工具调用的细节，直接以专家的口吻回答。
            """;
}