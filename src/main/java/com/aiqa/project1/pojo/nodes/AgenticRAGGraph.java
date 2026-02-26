package com.aiqa.project1.pojo.nodes;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.mapper.DocumentMapper;
import com.aiqa.project1.pojo.qa.RetrievalDecision;
import com.aiqa.project1.pojo.tag.OrganizationTag;
import com.aiqa.project1.service.impl.SpecialTagService;
import com.aiqa.project1.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.milvus.v2.service.vector.response.SearchResp;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Exceptions;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class AgenticRAGGraph {
    private final OpenAiChatModel douBaoLite;
    private final MilvusSearchUtils1 milvusSearchUtils1;
    private final QuerySplitter querySplitter;
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final SpecialTagService specialTagService;
    private SseEmitter sseEmitter = null;

    // 反思提示模板：评估回答质量，生成反思结论
    private static final String REFLECTION_TEMPLATE = """
            用户问题：%s
            当前回答：%s
            检索到的信息：%s
            请评估回答是否满足以下要求：
            1. 回答是否基于检索信息，无编造内容；
            2. 回答是否完整覆盖用户问题的核心诉求；
            3. 回答逻辑是否通顺，无明显错误。
            反思要求：
            - 若回答满足要求，输出"回答合格，可结束"；
            - 若回答不满足（如信息不足、逻辑错误），输出具体问题（如"检索信息未覆盖用户问题中的XX点"）。
            """;

    // 回答生成模板
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


    private static final String SUB_QUERY_TEMPLATE = """
            以下是子查询的内容：
            %s
            
            以下是检索到的相关信息：
            %s
            
            请根据检索信息回答查询，现在请生成回答：
            """;

    private static final String REWRITER_TEMPLATE = """
            你是一名专业的RAG 检索优化助手，核心职责是基于用户原始问题和问题反思内容，生成更精准、更清晰、更适配知识库检索的优化问题，提升检索召回的相关性与有效性。
            以下是你需要参考的信息：
            用户问题：%s
            
            
            反思内容：%s
            
            
            历史对话：%s
            
            
            
            请严格遵循以下要求完成问题重写：
            核心锚定：必须保留原始问题的核心诉求，不得偏离用户的根本意图。
            歧义消除：根据反思内容，修正原始问题中的模糊表述、多义词汇、指代不明等问题。
            信息补全：结合反思指出的信息缺失点（如缺少场景、条件、限定范围等），补充关键要素，让问题更具体。
            检索适配：调整问题的表述方式，使其更贴合知识库的语义结构，便于向量检索或关键词检索匹配到相关知识。
            简洁精炼：优化后的问题需简洁明了，避免冗余话术，同时避免过度扩展导致核心诉求弱化。
            文档提取与标注：若原始问题或反思内容中明确提及需要参照的文档全名，需精准提取该文档名称，为其添加书名号，并自然融入重写后的问题中；若提及多个文档，需全部提取并标注。
            注意：禁止添加问题里没有明确提及的文档名称
            """;

    private final RedisStoreUtils redisStoreUtils;
    private final CacheAsideUtils cacheAsideUtils;
    private final RabbitTemplate rabbitTemplate;
    private final DocumentMapper documentMapper;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AgenticRAGGraph(OpenAiChatModel douBaoLite, MilvusSearchUtils1 milvusSearchUtils1, QuerySplitter querySplitter, AsyncTaskExecutor asyncTaskExecutor, RedisStoreUtils redisStoreUtils, CacheAsideUtils cacheAsideUtils, RabbitTemplate rabbitTemplate, DocumentMapper documentMapper, SpecialTagService specialTagService) {
        this.douBaoLite = douBaoLite;
        this.milvusSearchUtils1 = milvusSearchUtils1;
        this.querySplitter = querySplitter;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.redisStoreUtils = redisStoreUtils;
        this.cacheAsideUtils = cacheAsideUtils;
        this.rabbitTemplate = rabbitTemplate;
        this.documentMapper = documentMapper;
        this.specialTagService = specialTagService;
    }

    public CompiledGraph<AgenticRAGState> buildGraph() throws GraphStateException {
        // 0. 开始节点
        AsyncNodeAction<AgenticRAGState> startAction = node_async(state -> {
            boolean RetrievalGlobalFlag = true;
            boolean RetrievalWebFlag = false;
            boolean LocalRetrievalFlag = true;
            boolean HistoryChatRequirements = false;

//            Integer sessionId = state.getSessionId();
//            QueryWrapper<Document> queryWrapper = new QueryWrapper<>();
//            List<Document> documentList;

//            //        queryWrapper.like("session_id", sessionId.toString());
//            queryWrapper.apply("FIND_IN_SET({0}, session_id) > 0", sessionId);

            // 获取user的所有tag
            List<OrganizationTag> tagList = specialTagService.getAllTagsByUserId_(state.getUserId());
            System.out.println("用户的标签为：" + tagList.stream().map(OrganizationTag::toString).collect(Collectors.joining("\n")));
//            List<String> tagNameList = tagList.stream().map(OrganizationTag::getTagName).toList();
//            // 过滤出符合标签的文档
//            queryWrapper
//                    .eq("user_id", state.getUserId())
//                    .or()
//                    .in("tag_type", tagNameList);
//
//            documentList = documentMapper.selectList(queryWrapper);
//
//            // 当前会话没有传递文件，用数据库进行回答
//            if (documentList == null || documentList.isEmpty()) {
//                throw new GraphStateException("没有符合的文档");
//            }
//
//            Map<String, Long> documentSizeMap = new HashMap<>();
//
//            String documentsName = documentList.stream()
//                    .map(document -> {
//                        documentSizeMap.put(document.getDocumentName(), document.getFileSize());
//                        return document.getDocumentName();
//                    })
//                    .collect(Collectors.joining(", "));
//            String query = "原问题：" + state.getQuery() + ((state.getRewriteQuery().isEmpty()) ? "": "\n反思重写后的问题：" + state.getRewriteQuery());
//
//            String prompt = CHOOSE_TEMPLATE.formatted(
//                    query,
//                    documentsName
//            );
//
//            String result = douBaoLite.chat(prompt);
//            RetrievalDecision retrievalDecision = this.parseLLMJson(result);
//            // 将相关文档以及检索来源写入state里
//            if (retrievalDecision != null) {
//                RetrievalWebFlag = retrievalDecision.isWebRetrievalFlag();
//                LocalRetrievalFlag = retrievalDecision.isLocalRetrievalFlag();
//                HistoryChatRequirements = retrievalDecision.isHistoryChatRequirements();
//
//            }

            return Map.of(
                    "retrieval_global_flag", RetrievalGlobalFlag,
                    "retrieval_web_flag", RetrievalWebFlag,
                    "local_retrieval_flag", LocalRetrievalFlag,
                    "history_chat_requirements", HistoryChatRequirements,
                    "tags", tagList,
                    "decision", DecisionType.RETRIEVE.toString()
                    );
        });

        // 1. 决策节点：判断下一步动作（检索/结束）
        AsyncNodeAction<AgenticRAGState> decisionAction = node_async(state -> {
            String currentDecision = state.getDecision();
            if (currentDecision.equals(DecisionType.FINISH.toString())) {
                rabbitTemplate.convertAndSend(
                        "mysql.update",
                        "chat.memory",
                        new State(
                                state.getUserId(),
                                state.getSessionId(),
                                state.getMemoryId(),
                                state.getQuery()
                        ));
            }
            // 首次进入/反思后需要重新检索 → 保持RETRIEVE；反思后回答合格 → 设为FINISH
            return Map.of("decision", currentDecision);
        });

        AsyncNodeAction<AgenticRAGState> rewriteAction = node_async(state -> {
            String currentDecision = state.getDecision();
            String currentReflection = state.getReflection();
            String chatMemory = redisStoreUtils.getChatMemory(
                    state.getUserId(),
                    state.getSessionId(),
                    100).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
            chatMemory = chatMemory.substring(chatMemory.length() - 10000 > 0 ? chatMemory.length() - 10000 : chatMemory.length());
            String chat = douBaoLite.chat(REWRITER_TEMPLATE.formatted(state.getQuery(), currentReflection, chatMemory));

            String memory = "<AI思考>原问题改写为" + chat;

            redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), memory);
            if (sseEmitter != null)
                sseEmitter.send(SseEmitter.event().name("chat").id(state.getSessionId().toString()).data(memory));
            // 首次进入/反思后需要重新检索 → 保持RETRIEVE；反思后回答合格 → 设为FINISH
            return Map.of(
                    "decision", currentDecision,
                    "rewrite_query", chat
            );
        });

        // 2. 检索节点：基于用户问题+过滤条件执行混合检索
        AsyncNodeAction<AgenticRAGState> retrievalAction = node_async(state -> {
            String query = "原问题：" + state.getQuery() + "\n反思重写后的问题：" + state.getRewriteQuery();

            List<SubQuery1> subQuery = querySplitter.splitQuery(
                    query,
                    state.getToolMetadataList()
            );
            String memory = "<AI思考>原问题经AI拆解为以下子问题：\n" + subQuery.stream().map(subQuery1 -> "    " + subQuery1.getSub_question()).collect(Collectors.joining("\n"));
            redisStoreUtils.setChatMemory(
                    state.getUserId(),
                    state.getSessionId(),
                    memory
            );

            if (sseEmitter != null)
                sseEmitter.send(SseEmitter.event().name("chat").id(state.getSessionId().toString()).data(memory));
            List<Map<String, List<String>>> retrievalInfo = new ArrayList<>();

            // 提取文档过滤条件
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

            // 子查询批量检索
            try {
                List<OrganizationTag> tags = state.getTags();

                List<String> partitionNames = new ArrayList<>(tags.stream()
                        .map(OrganizationTag::getTagName)
                        .toList());
                // 去除个人标签
                partitionNames.remove("PERSONAL");

                for (SubQuery1 sq : subQuery) {
                    SearchResp searchResp = milvusSearchUtils1.hybridSearch(
                            sq.getSub_question(),
                            sq.getSub_question(),
                            state.getUserId(),
                            partitionNames,
                            10, 60, filterExpr
                    );
                    List<String> contentList = new ArrayList<>(MilvusSearchUtils.getContentsFromSearchResp(searchResp)
                            .stream().map(Objects::toString).toList());
                    retrievalInfo.add(Map.of(sq.getSub_question(), contentList));
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
            return Map.of("retrieval_info", retrievalInfo);
        });

        // 3. 回答节点：基于检索信息生成回答
        AsyncNodeAction<AgenticRAGState> answerAction = node_async(state -> {

            List<Map<String, List<String>>> childQuery = state.getRetrievalInfo();
            List<String> subQuery1 = childQuery.stream().map(childQuery1 -> childQuery1.keySet().iterator().next()).toList();
            List<Supplier<SubQuery1>> task_list = new ArrayList<>();
            for (int i = 0; i < childQuery.size(); i++) {
                Map<String, List<String>> retrievalInfo = childQuery.get(i);
                String subQuery = subQuery1.get(i);
                List<String> contentList = retrievalInfo.get(subQuery);

                Supplier<SubQuery1> subQuerySupplier = () -> {
                    SubQuery1 query1 = new SubQuery1(subQuery, "");
                    String chat = douBaoLite.chat(SUB_QUERY_TEMPLATE.formatted(subQuery, contentList.toString()));
                    query1.setSub_answer(chat);
                    String chatMemory = "<AI思考>针对问题 %s 的回答为：%s".formatted(subQuery, chat);
                    redisStoreUtils.setChatMemory(
                            state.getUserId(),
                            state.getSessionId(),
                            chatMemory
                    );
                    if (sseEmitter != null) {
                        try {
                            sseEmitter.send(SseEmitter.event().name("chat").id(state.getSessionId().toString()).data(chatMemory));
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw Exceptions.propagate(e);
                        }
                    }

                    return query1;
                };
                task_list.add(subQuerySupplier);
            }
            List<SubQuery1> resultFuture = asyncTaskExecutor.submitAllWithResult(task_list).get();
            String finalAnswer = generateFinalAnswer(state, resultFuture);
            return Map.of("answer", finalAnswer);
        });

        // 4. 反思节点：评估回答质量，生成反思内容，并更新决策
        AsyncNodeAction<AgenticRAGState> reflectionAction = node_async(state -> {
            String answer = state.getAnswer();
            String prompt = REFLECTION_TEMPLATE.formatted(
                    state.getQuery(),
                    answer,
                    state.getRetrievalInfo().toString()
            );
            String reflection = douBaoLite.chat(prompt);

            // 根据反思结果更新决策：反思含"合格"则结束，否则重新检索
            String newDecision = reflection.contains("合格") ? DecisionType.FINISH.toString() : DecisionType.RETRIEVE.toString();
            String chat;
            if (newDecision.contains(DecisionType.FINISH.toString())) {
                chat = "<最终回答>" + answer;
                redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), chat);
            } else {
                chat = "<AI思考>" + answer;
                redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), chat);
            }

            if (sseEmitter != null) {
                try {
                    sseEmitter.send(SseEmitter.event().name("chat").id(state.getSessionId().toString()).data(chat));
                } catch (IOException e) {
                    e.printStackTrace();
                    throw e;
                }
            }

            return Map.of(
                    "reflection", reflection,
                    "decision", newDecision
            );
        });

        // 构建状态图：实现决策→检索→回答→反思→决策的闭环
        StateGraph<AgenticRAGState> graph = new StateGraph<>(AgenticRAGState.SCHEMA, AgenticRAGState::new)
                // 注册节点
                .addNode("start", startAction)
                .addNode("decision", decisionAction)
                .addNode("rewrite", rewriteAction)
                .addNode("retrieval", retrievalAction)
                .addNode("answer", answerAction)
                .addNode("reflection", reflectionAction)
                // 起始节点→决策节点
                .addEdge(START, "start")
                .addEdge("start", "decision")
                // 决策节点分支：RETRIEVE→检索节点，FINISH→END
                .addConditionalEdges("decision",
                        state -> CompletableFuture.supplyAsync(state::getDecision), // 分支判断依据：决策类型
                        Map.of(
                                DecisionType.RETRIEVE.toString(), "rewrite",
                                DecisionType.FINISH.toString(), END
                        )
                )
                // 检索→回答→反思→决策（闭环）
                .addEdge("rewrite", "retrieval")
                .addEdge("retrieval", "answer")
                .addEdge("answer", "reflection")
                .addEdge("reflection", "decision");

        // 编译图：启用内存检查点（保存状态，支撑闭环）
        CompileConfig compileConfig = CompileConfig.builder()
//                .checkpointSaver(new MemorySaver())
                .build();
        return graph.compile(compileConfig);
    }

    /**
     * 生成最终答案
     */
    private String generateFinalAnswer(AgenticRAGState originalQuery, List<SubQuery1> processedQueries) {
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