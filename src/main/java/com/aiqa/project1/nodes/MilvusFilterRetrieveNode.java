package com.aiqa.project1.nodes;

import com.aiqa.project1.utils.MilvusFilterContentRetriever;
import com.aiqa.project1.utils.MilvusFilterRetriever;
import com.aiqa.project1.utils.MilvusHybridRetriever;
import com.aiqa.project1.utils.MilvusQueryContentRetriever;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class MilvusFilterRetrieveNode implements Node{
    private final OpenAiChatModel douBaoLite;
    private final MilvusFilterRetriever milvusFilterRetriever;
    private static final String KEYWORD_EXTRACTION_TEMPLATE = """
            给定以下查询，你的任务是提取出该问题所在的文档名称。
            用户查询：%s
            至关重要的是，只需要输出文章名称，不需要输出其他字符，其他内容一概不要！不要在关键词前后添加任何内容！
            例如：
            2106.09685v2.pdf
            """;

    public MilvusFilterRetrieveNode(OpenAiChatModel douBaoLite, MilvusFilterRetriever milvusFilterRetriever, MilvusQueryContentRetriever milvusQueryContentRetriever, MilvusFilterContentRetriever milvusFilterContentRetriever) {
        this.douBaoLite = douBaoLite;
        this.milvusFilterRetriever = milvusFilterRetriever;
    }

    @Override
    public State run(State state) {

        Integer userId = state.getUserId();
        List<Content> retrievalInfo = state.getRetrievalInfo();
        String query = state.getRetrievalQuery();

        String prompt1 = KEYWORD_EXTRACTION_TEMPLATE.formatted(query);
        String keywords = douBaoLite.chat(prompt1);


        retrievalInfo.addAll(milvusFilterRetriever.retrieveTopK10(userId, keywords, Query.from(query)));
        return state;

    }
}
