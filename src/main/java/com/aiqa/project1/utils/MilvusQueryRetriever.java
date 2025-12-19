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

    public List<Content> retrieve(Integer userId, Object filteredWords, Boolean equalFlag, Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        QueryResp queryResp;
        try {
            if (filteredWords instanceof String ) {
                if (equalFlag) {
                    queryResp= milvusSearchUtils.filterByComeFromExact(userId, filteredWords.toString(), List.of("come_from", "text", "title", "author"));
                } else {
                    queryResp= milvusSearchUtils.filterByComeFromNotEqual(userId, filteredWords.toString(), List.of("come_from", "text", "title", "author"));
                }

            } else if (filteredWords instanceof List) {
                List<String> filteredWordsList = new ArrayList<>();
                ((List<?>) filteredWords).forEach(word -> filteredWordsList.add(word.toString()));
                queryResp= milvusSearchUtils.filterByComeFromIn(userId, filteredWordsList, List.of("come_from", "text", "title", "author"));
            }
            else {
                throw new RuntimeException("MilvusContentRetriever requires filtered words");
            }
            return MilvusSearchUtils.getContentsFromQueryResp(queryResp);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
