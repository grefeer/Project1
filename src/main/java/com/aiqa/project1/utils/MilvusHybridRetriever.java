package com.aiqa.project1.utils;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class MilvusHybridRetriever {

    private final MilvusSearchUtils milvusSearchUtils;


    public MilvusHybridRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }


    public List<Content> retrieve(Integer userId, String keywords, Object rerankerParams, Integer topK, Query query) {
        if (userId == null || keywords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.hybridSearch(query.text(), keywords, userId, topK, rerankerParams);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

    public List<Content> retrieveTopK10(Integer userId, String keywords, Object rerankerParams, Query query) {
        if (userId == null || keywords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.hybridSearch(query.text(), keywords, userId, 10, rerankerParams);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

    public List<Content> retrieveTopK10WithRRF(Integer userId, String keywords, Query query) {
        if (userId == null || keywords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.hybridSearch(query.text(), keywords, userId, 10, 60);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

}
