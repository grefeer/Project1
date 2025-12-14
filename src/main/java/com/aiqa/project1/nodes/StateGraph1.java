package com.aiqa.project1.nodes;

import com.aiqa.project1.utils.MilvusFilterContentRetriever;
import com.aiqa.project1.utils.MilvusHybridContentRetriever;
import com.aiqa.project1.utils.MilvusQueryContentRetriever;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
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
    private final RewriteNode rewriteNode;
    private final TranslationNode translationNode;
    private final ReflectionNode reflectionNode;
    private final WebSearchContentRetriever webSearchContentRetriever;

    private final MilvusFilterContentRetriever milvusFilterContentRetriever;
    private final MilvusHybridContentRetriever milvusHybridContentRetriever;
    private final MilvusQueryContentRetriever milvusQueryContentRetriever;

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

    public StateGraph1(OpenAiChatModel douBaoLite, RewriteNode rewriteNode, TranslationNode translationNode, ReflectionNode reflectionNode, WebSearchContentRetriever webSearchContentRetriever, MilvusFilterContentRetriever milvusFilterContentRetriever, MilvusHybridContentRetriever milvusHybridContentRetriever, MilvusQueryContentRetriever milvusQueryContentRetriever) {
        this.douBaoLite = douBaoLite;
        this.rewriteNode = rewriteNode;
        this.translationNode = translationNode;
        this.milvusFilterContentRetriever = milvusFilterContentRetriever;
        this.reflectionNode = reflectionNode;
        this.webSearchContentRetriever = webSearchContentRetriever;
        this.milvusHybridContentRetriever = milvusHybridContentRetriever;
        this.milvusQueryContentRetriever = milvusQueryContentRetriever;
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
                    .chatMemory(state.getChatMemory())
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
        retrieverToDescription.put(milvusHybridContentRetriever, "数据库混合检索执行器，先提取关键词，然后结合向量搜索（语义）和稀疏搜索（关键词）来检索 Milvus 数据库。最常用的检索场景，适用于绝大多数复杂、开放性问题，以平衡结果的召回率和精确性。");
        retrieverToDescription.put(milvusFilterContentRetriever, "数据库过滤检索执行器，首先从查询中提取文档名称等元数据，然后执行精确的向量搜索并强制限定在指定来源内。适用于用户明确提到了信息来源（如“在 XX 报告中...”、“关于 YY 文件”）的场景，保证答案只来自特定文档。");
        retrieverToDescription.put(milvusQueryContentRetriever, "数据库元数据查询执行器，不进行向量相似度计算，而是基于 Milvus 中的非向量字段（如 come_from）执行精确的数据库查询。适用于需要根据文档类型、作者或特定标签等元数据进行精确筛选的场景。");
        return retrieverToDescription;
    }

}
