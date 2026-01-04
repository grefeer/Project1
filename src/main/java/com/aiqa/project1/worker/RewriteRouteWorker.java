package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.*;
import com.aiqa.project1.utils.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 重写路由Worker
 * 负责问题重写、质量检查和路由决策，使用优化后的组件提高性能
 */
@Component
@Slf4j
public class RewriteRouteWorker {
    private final OpenAiChatModel douBaoLite;
    private final RabbitTemplate rabbitTemplate;
    private static final FallbackStrategy fallbackStrategy = FallbackStrategy.DO_NOT_ROUTE;
    private Map<String, String> agentsToDescription;
    
    @Autowired
    private RateLimiter rateLimiter;
    
    @Autowired
    private TimeoutControl timeoutControl;
    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;
    


//    private static final String ROUTING_TEMPLATE = """
//    你是多智能体系统的路由节点，负责根据历史对话和用户查询选择最合适的数据源。以下是可用的节点信息：
//    <节点信息>
//    %s
//    </节点信息>
//
//    请先查看历史对话：
//    <历史对话>
//    %s
//    </历史对话>
//
//    当前用户查询：
//    <用户查询>
//    %s
//    </用户查询>
//
//    你的任务是从上述节点中选择检索相关信息的最合适数据源(只有问题十分复杂时才能多选)。请遵循以下规则：
//    1. 若历史对话中已使用数据库检索节点但未找到结果，优先选择网络检索节点。
//    2. 仅返回节点对应的数字编号，多个编号用逗号分隔，不得包含其他内容。
//    现在，请输出你的选择。
//    """;
    
    private final String QUALITY_INSPECTION_TEMPLATE = """
    你的任务是快速检验"查询重写节点任务"生成的双语查询内容是否符合要求。内容准确性为核心检验标准，格式问题暂不考虑。
    首先，请仔细阅读原查询重写任务的全部内容：
    <原任务内容>
    查询重写节点任务:根据提供的历史对话和最新用户查询，生成一个简洁的中英文双语查询。该查询的答案需要包含现有对话中回答的不足之处。
    查询重写节点输出格式:
    <双语查询>
    中文:[生成的中文查询]
    英文:[对应的英文查询]
    </双语查询>
    查询重写节点任务要求:
    1. 中文查询和英文查询需一一对应，表达相同的核心需求
    2. 双语查询需简洁明了，避免冗余信息
    3. 核心要求:查询的答案必须能够补充现有对话中回答的不足之处(即现有回答未覆盖、解释不清或存在偏差的内容)
    </原任务内容>

    接下来，请查看待检验的重写后双语查询：
    <待检验查询>
    %s
    </待检验查询>

    检验标准如下：
    1. 生成的双语查询是否直接针对现有对话回答的不足之处(核心要求)
    2. 中文与英文查询是否一一对应，表达相同核心需求
    3. 内容是否简洁，无冗余无关信息

    请按照以下规则输出结果：
    - 若内容完全符合要求(无错误)：在<检验结果>标签内原封不动重述待检验的双语查询，不得有任何修改
    - 若内容存在错误(如未针对回答不足、双语不对应、冗余等)：在<检验结果>标签内重新生成符合要求的双语查询，需严格遵循原任务的双语格式和核心要求

    <检验结果>
    [按规则输出的内容]
    </检验结果>
    """;
    
    private static final String QUERY_REWRITING_TEMPLATE = """
    你的任务是根据提供的历史对话和最新用户查询，生成一个简洁的中英文双语查询。该查询的答案需要包含现有对话中回答的不足之处。
    首先，请仔细阅读以下历史对话：
    <历史对话>
    %s
    </历史对话>
    
    接下来，请查看上一次反思的查询以及目标查询：
    <上一次反思的查询>
    %s
    </上一次反思的查询>
    <目标查询>
    %s
    </目标查询>
    
    请按照以下要求生成双语查询:
    1. 中文查询和英文查询需一一对应，表达相同的核心需求
    2. 双语查询需简洁明了，避免冗余信息
    3. 核心要求:查询的答案必须能够补充现有对话中回答的不足之处(即现有回答未覆盖、解释不清或存在偏差的内容)
    4. 中英文查询需分别独立成句，中文在前，英文在后

    请在<双语查询>标签内输出结果，格式如下:
    <双语查询>
    中文:[生成的中文查询]
    英文:[对应的英文查询]
    </双语查询>

    注意:请确保生成的查询直接针对现有回答的不足，无需包含无关内容。
    """;

    private final RedisStoreUtils redisStoreUtils;

    public RewriteRouteWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.agentsToDescription = getObjectStringMap();
        this.redisStoreUtils = redisStoreUtils;
    }

    /**
     * 监听重写和路由队列
     * @param state 状态对象
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = SystemConfig.REWRITE_ROUTE_QUEUE, durable = "true"),
            exchange = @Exchange(name = SystemConfig.REFLECTION_DIRECT, type = ExchangeTypes.DIRECT),
            key = {SystemConfig.HAVE_PROBLEM_KEY, SystemConfig.NEW_PROBLEM_KEY}
    ))
    public void run(State state) {
        // 限流控制
        if (!rateLimiter.tryAcquire()) {
            throw new RuntimeException("系统繁忙，请稍后再试");
        }
        
        try {
            // 准备路由选项
            List<String> agentList = prepareAgentOptions();

            // 1. 问题重写
            String chatHistory = redisStoreUtils.getChatMemory(
                state.getUserId(),
                state.getSessionId(),
                SystemConfig.MAX_REWRITE_HISTORY_SIZE).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
            String rewriteQuery = rewriteUserQuery(
                    chatHistory,
                    (state.getRetrievalQuery() == null || state.getRetrievalQuery().isEmpty()) ? "": state.getRetrievalQuery(),
                    state.getQuery()
            );
            
            // 2. 问题质检
            rewriteQuery = QualityInspection(rewriteQuery)
            .replace("<双语查询>", "")
            .replace("<检验结果>", "")
            .replace("</检验结果>", "")
            .replace("</双语查询>", "");
            state.setRetrievalQuery(rewriteQuery);
            
            // 3. 路由，决定使用哪一个智能体
            routeToAgents(state, agentList, chatHistory, rewriteQuery);
            
        } catch (Exception e) {
            log.error("StateGraph处理异常: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            // 释放限流许可
            rateLimiter.release();
        }
    }
    
    /**
     * 准备路由选项
     * @return 可用的智能体列表
     */
    private List<String> prepareAgentOptions() {
        List<String> agentList = new ArrayList<>();
        for(Map.Entry<String, String> entry : agentsToDescription.entrySet()) {
            agentList.add(entry.getValue());
        }
        log.debug("准备路由选项，共{}个智能体", agentList.size());
        return agentList;
    }
    
    /**
     * 问题重写功能
     * @param chatHistory 历史对话
     * @param originalQuery 原始查询
     * @return 重写后的查询
     */
    private String rewriteUserQuery(String chatHistory, String subQuery, String originalQuery) {
        String rewritePrompt = QUERY_REWRITING_TEMPLATE.formatted(chatHistory, subQuery, originalQuery);
        return timeoutControl.chatWithTimeout(douBaoLite, rewritePrompt);
    }
    
    /**
     * 质量检查功能
     * @param query 待检查的查询
     * @return 检查后的查询
     */
    private String QualityInspection(String query) {
        String qualityPrompt = QUALITY_INSPECTION_TEMPLATE.formatted(query);
        return timeoutControl.chatWithTimeout(douBaoLite, qualityPrompt);
    }
    
    /**
     * 路由功能，决定使用哪个智能体，使用异步处理提高性能
     * @param state 状态对象
     * @param agentList 智能体列表
     * @param chatHistory 历史对话
     * @param rewriteQuery 重写后的查询
     */
    private void routeToAgents(State state, List<String> agentList, String chatHistory, String rewriteQuery) {
        // 如果不是数据库检索，直接使用网络检索
//        if (state.getRetrievalWebFlag()) {
//            Boolean ifExistence = redisStoreUtils.putRetrievalCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), state.getParams(), 1);
//
//            // 使用异步任务执行器发送消息
//            asyncTaskExecutor.submit(() -> {
//                rabbitTemplate.convertAndSend(SystemConfig.RETRIEVE_EXCHANGE, "WebSearch.retrieve", state);
//                return null;
//            });
//            return;
//        }
        
        // 构建路由提示
        StringBuilder optionsBuilder = new StringBuilder();
        int id = 1;
        for(Map.Entry<String, String> entry : agentsToDescription.entrySet()) {
            if (id > 1) {
                optionsBuilder.append("\n");
            }
            optionsBuilder.append(id);
            optionsBuilder.append(": ");
            optionsBuilder.append(entry.getKey());
            id++;
        }

        Collection<String> matchedAgents = getMatchedAgents(state);

//        String prompt1 = ROUTING_TEMPLATE.formatted(
//                optionsBuilder.toString(),
//                chatHistory,
//                rewriteQuery
//        );
//        // 使用异步任务执行器处理路由决策
//        CompletableFuture<Collection<String>> routingFuture = asyncTaskExecutor.submit(() ->
//            route(prompt1, agentList)
//        );
        
        try {
//            // 等待路由决策完成
//            matchedAgents = routingFuture.get(SystemConfig.AI_CHAT_TIMEOUT, TimeUnit.MILLISECONDS);


            if (matchedAgents.isEmpty()) {
                // 直接回答问题
                rabbitTemplate.convertAndSend(SystemConfig.ANSWER_TOPIC, SystemConfig.HAVE_GATHERED_RETRIEVE_KEY, state);
                return;
            }

            // 统计使用了几个检索器，方便后续合并检索信息
            state.setMaxRetrievalCount(matchedAgents.size());
            Boolean ifExistence = redisStoreUtils.putRetrievalCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), state.getParams(), state.getMaxRetrievalCount());

            // 使用异步任务执行器批量发送消息
            List<CompletableFuture<Object>> sendFutures = matchedAgents.stream()
                    .filter(Objects::nonNull)
                    .map(s -> asyncTaskExecutor.submit(() -> {
                        rabbitTemplate.convertAndSend(SystemConfig.RETRIEVE_EXCHANGE, s + ".retrieve", state);
                        return null;
                    }))
                    .collect(Collectors.toList());
            
            // 等待所有消息发送完成
            CompletableFuture.allOf(sendFutures.toArray(new CompletableFuture[0]))
                .get(SystemConfig.RABBITMQ_SEND_TIMEOUT, TimeUnit.MILLISECONDS);
                
        } catch (Exception e) {
            log.error("路由处理失败: {}", e.getMessage());
            throw new RuntimeException("路由处理失败", e);
        }
    }

    @NotNull
    private static Collection<String> getMatchedAgents(State state) {
        Collection<String> matchedAgents = new ArrayList<>();
        // 如果需要网络搜索，则进行网络搜索
        if (state.getRetrievalWebFlag())
            matchedAgents.add(WebSearchWorker.class.getSimpleName());
        // 如果有提及文件名，则进行文件名查询,多个文件则进行MilvusFilterRetrieveWorker，单个文件则进行MilvusQueryRetrieveWorker，
        // startWorker中要加入是否需要进行本地数据库检索的评估，评估需要查询则进行混合检索
        if (state.getLocalRetrievalFlag()) {
            int retrievalDocument = state.getRetrievalDocuments().size();
            if (retrievalDocument == 0) {
                // 查询没有提及文档，但是要在数据库里查询
                matchedAgents.add(MilvusHybridRetrieveWorker.class.getSimpleName());
            } else if (retrievalDocument <= 2) {
                // 只提及 1~2 个文档, 将文档放到
                matchedAgents.add(MilvusQueryRetrieveWorker.class.getSimpleName());
            } else {
                matchedAgents.add(MilvusFilterRetrieveWorker.class.getSimpleName());
            }
        }
        if (state.getRetrievalWebFlag()) {
            matchedAgents.add(WebSearchWorker.class.getSimpleName());
        }
        return matchedAgents;
    }

    public enum FallbackStrategy {
        DO_NOT_ROUTE,
        ROUTE_TO_ALL,
        FAIL;
    }

    public Collection<String> route(String query, List<String> agentList) {
        try {
            String response = timeoutControl.chatWithTimeout(douBaoLite, query);

            // 验证响应格式
            if (!isValidRoutingResponse(response)) {
                throw new IllegalArgumentException("无效的路由响应格式: " + response);
            }
            return parse(response, agentList);
        } catch (Exception e) {
            return fallback(e);
        }
    }

    private boolean isValidRoutingResponse(String response) {
        // 简单验证是否为数字和逗号组成
        return response != null && response.matches("^\\d+(,\\s*\\d+)*$");
    }

    protected Collection<String> fallback(Exception e) {
        Collection<String> nodes;
        switch (fallbackStrategy) {
            case DO_NOT_ROUTE -> nodes = Collections.emptyList();
            case ROUTE_TO_ALL -> nodes = new ArrayList<>(this.agentsToDescription.values());
            default -> throw new RuntimeException(e);
        }
        return nodes;
    }

    protected Collection<String> parse(String choices, List<String> agentList) {
        // 1. 过滤空字符串, 避免分割出空元素
        if (choices == null || choices.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return Arrays.stream(choices.split(","))
                    .map(String::trim)
                    // 2. 转为Integer, 并校验是否为有效数字
                    .map(idStr -> {
                        try {
                            return Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            log.error("无效的ID格式: {}", idStr);
                            return null; // 标记无效ID
                        }
                    })
                    // 3. 过滤无效ID(null、小于1、大于选项总数)
                    .filter(id -> id != null && id >= 1 && id <= agentList.size())
                    // 4. 关键：1-based ID 转 0-based 索引(减1)
                    .map(id -> agentList.get(id - 1))
                    // 5. 过滤可能的null Node(避免注入失败导致的null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("解析ID失败：{}", choices);
            return Collections.emptyList();
        }
    }

    private Map<String, String> getObjectStringMap() {
        Map<String, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put("数据库混合检索执行器，先提取关键词，然后结合向量搜索(语义)和稀疏搜索(关键词)来检索 Milvus 数据库。严格按照以下要求执行：只要用户问题中明确提及了文档名称，就禁止用混合检索器", MilvusRetrieverName.HYBRID_RETRIEVER.getClassName());
        retrieverToDescription.put("数据库过滤检索执行器，首先从查询中提取文档名称等元数据，然后执行精确的向量搜索并强制限定在指定来源内。只适用于用户明确提到了信息来源(如'在 XX 报告中...'、'关于 YY 文件')的场景，保证答案只来自特定文档。", MilvusRetrieverName.FILTER_RETRIEVER.getClassName());
        retrieverToDescription.put("数据库元数据检索执行器，不进行向量相似度计算，而是基于 Milvus 中的非向量字段(如 come_from)执行精确的数据库查询。只适用于问题中给出明确标题的情况。", MilvusRetrieverName.QUERY_RETRIEVER.getClassName());

        return retrieverToDescription;
    }
}