package com.aiqa.project1.nodes;

import com.aiqa.project1.utils.MilvusFilterContentRetriever;
import com.aiqa.project1.utils.MilvusHybridRetriever;
import com.aiqa.project1.utils.MilvusQueryContentRetriever;
import com.aiqa.project1.utils.MilvusQueryRetriever;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Component
public class MilvusQueryRetrieveNode implements Node{
    private final OpenAiChatModel douBaoLite;
    private final MilvusQueryRetriever milvusQueryRetriever;
    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出三个最能代表该查询的关键词。
            用户查询：%s
            至关重要的是，关键词之间用英文逗号间隔开，并且你只需提供三个关键词，其他内容一概不要！不要在关键词前后添加任何内容！
            """;

    public MilvusQueryRetrieveNode(OpenAiChatModel douBaoLite, MilvusQueryRetriever milvusQueryRetriever) {
        this.douBaoLite = douBaoLite;
        this.milvusQueryRetriever = milvusQueryRetriever;

    }

    @Override
    public State run(State state) {

        Integer userId = state.getUserId();
        List<Content> retrievalInfo = state.getRetrievalInfo();
        String query = state.getRetrievalQuery();

        String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        String keywords = douBaoLite.chat(prompt1);

        List<String> threadSafeKeywordsList = Collections.synchronizedList(new ArrayList<>());
        Collections.addAll(threadSafeKeywordsList, keywords.split(","));

        retrievalInfo.addAll(milvusQueryRetriever.retrieve(userId, threadSafeKeywordsList, true, Query.from(query)));
        return state;

    }
}
