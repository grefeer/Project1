package com.aiqa.project1.utils;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
public class MilvusQueryRetriever {

    private final MilvusSearchUtils milvusSearchUtils;


    public MilvusQueryRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }

    public List<Content> retrieve(Integer userId,Integer sessionId, String filteredWords, Boolean equalFlag, Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        QueryResp queryResp;
        try {
            queryResp = milvusSearchUtils.filterByComeFromIn(userId, sessionId, filteredWords, List.of("come_from", "text", "title", "author"));
            return MilvusSearchUtils.getContentsFromQueryResp(queryResp);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
