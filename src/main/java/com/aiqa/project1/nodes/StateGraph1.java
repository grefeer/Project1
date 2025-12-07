package com.aiqa.project1.nodes;

import com.aiqa.project1.utils.MilvusHybridContentRetriever;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;


/**
 *
 */
@Component
public class StateGraph1 implements Node{
    private final OpenAiChatModel douBaoLite;
    private final MilvusHybridRetrieveNode milvusHybridRetrieveNode;
    private final RewriteNode rewriteNode;
    private final TranslationNode translationNode;
    private final WebSearchNode webSearchNode;
    private final AnswerNode answerNode;
    private final ReflectionNode reflectionNode;
    private final WebSearchContentRetriever webSearchContentRetriever;
    private final MilvusHybridContentRetriever milvusHybridContentRetriever;

    private final Map<ContentRetriever, String> agentsToDescription; // 只声明，不初始化
    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出三个最能代表该查询的关键词。
            用户查询：%s
            至关重要的是，关键词之间用英文逗号间隔开，并且你只需提供三个关键词，其他内容一概不要！不要在关键词前后添加任何内容！
            """;

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

    public StateGraph1(OpenAiChatModel douBaoLite, MilvusHybridRetrieveNode milvusHybridRetrieveNode, RewriteNode rewriteNode, TranslationNode translationNode, WebSearchNode webSearchNode, AnswerNode answerNode, ReflectionNode reflectionNode, WebSearchContentRetriever webSearchContentRetriever, MilvusHybridContentRetriever milvusHybridContentRetriever) {
        this.douBaoLite = douBaoLite;
        this.milvusHybridRetrieveNode = milvusHybridRetrieveNode;
        this.rewriteNode = rewriteNode;
        this.translationNode = translationNode;
        this.webSearchNode = webSearchNode;
        this.reflectionNode = reflectionNode;
        this.webSearchContentRetriever = webSearchContentRetriever;
        this.answerNode = answerNode;
        this.milvusHybridContentRetriever = milvusHybridContentRetriever;
        this.agentsToDescription = getObjectStringMap();
    }

    public State run(State state) {
        try {

            QueryRouter queryRouter = new LanguageModelQueryRouter(douBaoLite, agentsToDescription);

            RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                    .queryRouter(queryRouter)
                    .build();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(douBaoLite)
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();

            for (int i = 0; i < state.getMaxReflection(); i++) {

                // 问题重写
                state = rewriteNode.run(state);
                String rewrittenQuery= state.getRetrievalQuery();

                // 翻译文本
                state =  translationNode.run(state);

                // 路由，决定使用哪一个智能体
                String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(state.getRetrievalQuery());
                String keywords = douBaoLite.chat(prompt1);
                milvusHybridContentRetriever.setKeywords(keywords);
                milvusHybridContentRetriever.setUserId(state.getUserId());
                state.getChatMemory().add(AiMessage.from(assistant.chat(state.getRetrievalQuery())));

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

    interface Assistant {
        String chat(@UserMessage String userMessage);
    }

    private Map<ContentRetriever, String> getObjectStringMap() {
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(milvusHybridContentRetriever, "数据库检索器，用于从数据库中检索信息，先选择该检索器");
        retrieverToDescription.put(webSearchContentRetriever, "网络检索器，用于从网络中检索信息，如果数据库中没有，再选用该检索器");
        return retrieverToDescription;
    }

}
