package com.aiqa.project1.utils;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class MilvusFilterContentRetriever implements ContentRetriever {

    private final MilvusSearchUtils milvusSearchUtils;

    @Setter
    private Integer userId = null;

    @Setter
    private String filteredWords = null;

    @Setter
    private Integer topK = 10;

//    @Setter
//    private String keywords;


    public MilvusFilterContentRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.filterSearch(query.text(), filteredWords, userId, 0, topK);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

}
