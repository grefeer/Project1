package com.aiqa.project1.utils;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Setter;
import org.springframework.stereotype.Component;
import java.util.List;


@Component
public class MilvusHybridContentRetriever implements ContentRetriever {

    private final MilvusSearchUtils milvusSearchUtils;

    @Setter
    private Integer userId = null;

    @Setter
    private String keywords = null;

    @Setter
    private Object rerankerParams = 60;

    @Setter
    private Integer topK = 10;

//    @Setter
//    private String keywords;


    public MilvusHybridContentRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }


    @Override
    public List<Content> retrieve(Query query) {
        if (userId == null || keywords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.hybridSearch(query.text(), keywords, userId, topK, rerankerParams);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

}
