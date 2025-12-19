package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.*;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


/**
 *
 */
@Component
public class RewriteRouteWorker {
    private final OpenAiChatModel douBaoLite;
    private final RewriteNode rewriteNode;
    private final TranslationNode translationNode;
    private final RabbitTemplate rabbitTemplate;
    private static final FallbackStrategy fallbackStrategy = FallbackStrategy.ROUTE_TO_ALL;
    private final Map<String, String> agentsToDescription; // 只声明，不初始化


    private static final String ROUTING_TEMPLATE = """
            你是多智能体系统的路由节点，负责根据历史对话和用户查询选择最合适的数据源。以下是可用的节点信息：
            <节点信息>
            %s
            </节点信息>
            
            请先查看历史对话：
            <历史对话>
            %s
            </历史对话>
            
            当前用户查询：
            <用户查询>
            %s
            </用户查询>
            
            你的任务是从上述节点中选择检索相关信息的最合适数据源（只有问题十分复杂时才能多选）。请遵循以下规则：
            1. 若历史对话中已使用数据库检索节点但未找到结果，优先选择网络检索节点。
            2. 仅返回节点对应的数字编号，多个编号用逗号分隔，不得包含其他内容。
            现在，请输出你的选择。
            """;
    private final String TRANSLATION_TEMPLATE = """
                请将以下文本准确翻译成其他的语言，仅返回翻译结果，不添加任何额外内容：
                <文本>
                %s
                </文本>
                要求：
                1、如果是文本语言是中文，将其翻译为英文；
                2、如果是文本语言是英文，将其翻译为中文；
                现在，请按照要求翻译文本。
                """;
    private static final String QUERY_REWRITING_TEMPLATE = """
            你的任务是根据提供的历史对话和后续用户查询，生成一个简洁、独立的综合查询。该查询需完整融入历史对话的上下文信息，清晰保持用户的原始意图，且无需依赖额外背景即可理解。
            首先，请阅读以下历史对话：
            <历史对话>
            %s
            </历史对话>
            接下来，请阅读用户的后续查询：
            <用户查询>
            %s
            </用户查询>
            请严格按照以下要求生成综合查询：
            1. 必须包含历史对话中的关键上下文信息
            2. 必须准确反映用户的核心需求和意图
            3. 表述需清晰、具体，避免模糊
            4. 生成的查询需独立完整，无需参考原始对话即可理解
            5. 保持简洁，避免冗余信息，必须少于100词
            至关重要的是，你只需输出重新表述后的独立查询文本，不得添加任何额外说明、解释或格式标记！
            """;

    private final RedisStoreUtils redisStoreUtils;

    public RewriteRouteWorker(OpenAiChatModel douBaoLite, RewriteNode rewriteNode, TranslationNode translationNode, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rewriteNode = rewriteNode;
        this.translationNode = translationNode;
        this.rabbitTemplate = rabbitTemplate;
        this.agentsToDescription = getObjectStringMap();
        this.redisStoreUtils = redisStoreUtils;
    }

    /**
     * 监听重写和路由队列
     * @param state
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "rewrite.route", durable = "true"),
            exchange = @Exchange(name = "refection.direct", type = ExchangeTypes.DIRECT),
            key = {"have.problem", "new.problem"}
    ))
    public void run(State state) {
        try {
            StringBuilder optionsBuilder = new StringBuilder();
            int id = 1;
            List<String> agentList = new ArrayList<>();

            for(Map.Entry<String, String> entry : agentsToDescription.entrySet()) {

                if (id > 1) {
                    optionsBuilder.append("\n");
                }

                agentList.add(entry.getValue());
                optionsBuilder.append(id);
                optionsBuilder.append(": ");
                optionsBuilder.append(entry.getKey());
                id++;
            }
            System.out.println(agentList.getFirst().getClass().getName());

            // 问题重写
            String chatHistory = redisStoreUtils.getChatMemory(state.getUserId(), state.getSessionId(), 5).stream().map(Object::toString).collect(Collectors.joining("\n"));
            String rewritePrompt = QUERY_REWRITING_TEMPLATE.formatted(chatHistory, state.getQuery());
            String rewriteQuery = douBaoLite.chat(rewritePrompt);
            state.setRetrievalQuery(rewriteQuery);


            // 翻译问题
            String translationPrompt = TRANSLATION_TEMPLATE.formatted(rewriteQuery);
            String translatedSentence = douBaoLite.chat(translationPrompt);

            rewriteQuery = rewriteQuery + "(%s)".formatted(translatedSentence);
            state.setRetrievalQuery(rewriteQuery);

            // 路由，决定使用哪一个智能体
            String prompt1 = ROUTING_TEMPLATE.formatted(
                    optionsBuilder.toString(),
                    chatHistory,
                    rewriteQuery
            );
            if (! state.getRetrievalDBFlag()) {
                Boolean ifExistence =  redisStoreUtils.putRetrievalCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), 1);
                rabbitTemplate.convertAndSend("Retrieve", "WebSearch.retrieve", state);
                return;
            }
            Collection<String> matchedAgents = route(prompt1, agentList);

            if (matchedAgents.isEmpty()) {
                // 直接回答问题
                rabbitTemplate.convertAndSend("answer.topic", "have.gathered.retrieve", state);
                return;
            }

            // 统计使用了几个检索器，方便后续合并检索信息
            state.setMaxRetrievalCount(matchedAgents.size());
            Boolean ifExistence =  redisStoreUtils.putRetrievalCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), state.getMaxRetrievalCount());

            matchedAgents.stream()
                    .filter(Objects::nonNull)
                    .forEach(s -> rabbitTemplate.convertAndSend("Retrieve", s + ".retrieve", state));


        } catch (Exception e) {
            System.err.println("StateGraph处理异常: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public enum FallbackStrategy {
        DO_NOT_ROUTE,
        ROUTE_TO_ALL,
        FAIL;
    }



    public Collection<String> route(String query, List<String> agentList) {
        try {
            String response = douBaoLite.chat(query);
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
        // 1. 过滤空字符串，避免分割出空元素
        if (choices == null || choices.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return Arrays.stream(choices.split(","))
                    .map(String::trim)
                    // 2. 转为Integer，并校验是否为有效数字
                    .map(idStr -> {
                        try {
                            return Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            System.err.println("无效的ID格式：" + idStr);
                            return null; // 标记无效ID
                        }
                    })
                    // 3. 过滤无效ID（null、小于1、大于选项总数）
                    .filter(id -> id != null && id >= 1 && id <= agentList.size())
                    // 4. 关键：1-based ID 转 0-based 索引（减1）
                    .map(id -> agentList.get(id - 1))
                    // 5. 过滤可能的null Node（避免注入失败导致的null）
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("解析ID失败：" + choices);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private Map<String, String> getObjectStringMap() {
        Map<String, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put("数据库混合检索执行器，先提取关键词，然后结合向量搜索（语义）和稀疏搜索（关键词）来检索 Milvus 数据库。最常用的检索场景，适用于绝大多数复杂、开放性问题，以平衡结果的召回率和精确性。", MilvusRetrieverName.HYBRID_RETRIEVER.getClassName());
        retrieverToDescription.put("数据库过滤检索执行器，首先从查询中提取文档名称等元数据，然后执行精确的向量搜索并强制限定在指定来源内。只适用于用户明确提到了信息来源（如“在 XX 报告中...”、“关于 YY 文件”）的场景，保证答案只来自特定文档。", MilvusRetrieverName.FILTER_RETRIEVER.getClassName());
        retrieverToDescription.put("数据库元数据检索执行器，不进行向量相似度计算，而是基于 Milvus 中的非向量字段（如 come_from）执行精确的数据库查询。只适用于问题中给出明确标题的情况。", MilvusRetrieverName.QUERY_RETRIEVER.getClassName());
//        retrieverToDescription.put("网络检索节点，用于从网络中检索信息，先使用数据库节点，如果数据库中没有相关信息，再考虑选用该节点", MilvusRetrieverName.WEB_SEARCH_RETRIEVER.getClassName());

        return retrieverToDescription;
    }

}
