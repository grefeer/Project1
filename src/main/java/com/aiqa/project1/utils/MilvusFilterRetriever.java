package com.aiqa.project1.utils;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class MilvusFilterRetriever {

    private final MilvusSearchUtils milvusSearchUtils;

    public MilvusFilterRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }

    public List<Content> retrieve(Integer userId, Integer sessionId, String filteredWords, Integer topK, Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.filterSearch(query.text(), filteredWords, userId, sessionId, topK);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

    public List<Content> retrieveTopK10(Integer userId, Integer sessionId, String filteredWords, Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        SearchResp searchResp = milvusSearchUtils.filterSearch(query.text(), filteredWords, userId, sessionId, 10);
        return MilvusSearchUtils.getContentsFromSearchResp(searchResp);
    }

}
