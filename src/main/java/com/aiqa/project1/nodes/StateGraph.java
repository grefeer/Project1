package com.aiqa.project1.nodes;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


/**
 *
 */
//@Component
public class StateGraph implements Node{
    private final OpenAiChatModel douBaoLite;
    private final MilvusHybridRetrieveNode milvusHybridRetrieveNode;
    private final MilvusFilterRetrieveNode milvusFilterRetrieveNode;
    private final MilvusQueryRetrieveNode milvusQueryRetrieveNode;
    private final RewriteNode rewriteNode;
    private final TranslationNode translationNode;
    private final WebSearchNode webSearchNode;
    private final AnswerNode answerNode;
    private final ReflectionNode reflectionNode;

    private static final FallbackStrategy fallbackStrategy = FallbackStrategy.ROUTE_TO_ALL;
    private final Map<String, Node> agentsToDescription; // 只声明，不初始化


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

    public StateGraph(OpenAiChatModel douBaoLite, MilvusHybridRetrieveNode milvusHybridRetrieveNode, MilvusFilterRetrieveNode milvusFilterRetrieveNode, MilvusQueryRetrieveNode milvusQueryRetrieveNode, RewriteNode rewriteNode, TranslationNode translationNode, WebSearchNode webSearchNode, AnswerNode answerNode, ReflectionNode reflectionNode) {
        this.douBaoLite = douBaoLite;
        this.milvusHybridRetrieveNode = milvusHybridRetrieveNode;
        this.milvusFilterRetrieveNode = milvusFilterRetrieveNode;
        this.milvusQueryRetrieveNode = milvusQueryRetrieveNode;
        this.rewriteNode = rewriteNode;
        this.translationNode = translationNode;
        this.webSearchNode = webSearchNode;
        this.reflectionNode = reflectionNode;
        this.answerNode = answerNode;
        this.agentsToDescription = getObjectStringMap();
    }

    public State run(State state) {
        try {
            StringBuilder optionsBuilder = new StringBuilder();
            int id = 1;
            List<Node> agentList = new ArrayList<>();

            for(Map.Entry<String, Node> entry : agentsToDescription.entrySet()) {

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

            for (int i = 0; i < state.getMaxReflection(); i++) {

                // 问题重写
                state = rewriteNode.run(state);
                String chatHistory = state.getChatMemory().messages().stream().map(Object::toString).collect(Collectors.joining("\n"));
                String rewrittenQuery= state.getRetrievalQuery();

                // 翻译文本
                state =  translationNode.run(state);

                // 路由，决定使用哪一个智能体
                String prompt1 = ROUTING_TEMPLATE.formatted(optionsBuilder.toString(), chatHistory, rewrittenQuery);
                Collection<Node> matchedAgents = route(prompt1, agentList);

                State finalState = state;
                matchedAgents.stream()
                        .filter(Objects::nonNull)
                        .forEach(agent -> agent.run(finalState));
                // 回答
                state = answerNode.run(finalState);

                // 质检
                state = reflectionNode.run(state);
                AiMessage reflectionMessage = (AiMessage)(state.getChatMemory().messages().getLast());
                if (reflectionMessage.text().equals("无")) break;
            }

            return state;
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

    public Collection<Node> route(String query, List<Node> agentList) {
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


    protected Collection<Node> fallback(Exception e) {
        Collection<Node> nodes;
        switch (fallbackStrategy) {
            case DO_NOT_ROUTE -> nodes = Collections.emptyList();
            case ROUTE_TO_ALL -> nodes = new ArrayList<>(this.agentsToDescription.values());
            default -> throw new RuntimeException(e);
        }
        return nodes;
    }


    protected Collection<Node> parse(String choices, List<Node> agentList) {
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

    private Map<String, Node> getObjectStringMap() {
        Map<String, Node> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put("数据库混合检索执行器，先提取关键词，然后结合向量搜索（语义）和稀疏搜索（关键词）来检索 Milvus 数据库。最常用的检索场景，适用于绝大多数复杂、开放性问题，以平衡结果的召回率和精确性。", milvusHybridRetrieveNode);
        retrieverToDescription.put("数据库过滤检索执行器，首先从查询中提取文档名称等元数据，然后执行精确的向量搜索并强制限定在指定来源内。适用于用户明确提到了信息来源（如“在 XX 报告中...”、“关于 YY 文件”）的场景，保证答案只来自特定文档。", milvusFilterRetrieveNode);
        retrieverToDescription.put("数据库元数据查询执行器，不进行向量相似度计算，而是基于 Milvus 中的非向量字段（如 come_from）执行精确的数据库查询。适用于需要根据文档类型、作者或特定标签等元数据进行精确筛选的场景。", milvusQueryRetrieveNode);
//        retrieverToDescription.put("网络检索节点，用于从网络中检索信息，先使用数据库节点，如果数据库中没有相关信息，再考虑选用该节点", webSearchNode);

        return retrieverToDescription;
    }

}
