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
public class MilvusQueryContentRetriever implements ContentRetriever {

    private final MilvusSearchUtils milvusSearchUtils;

    @Setter
    private Integer userId = null;

    @Setter
    private Object filteredWords = null;

    @Setter
    private Boolean equalFlag = true;


    public MilvusQueryContentRetriever(MilvusSearchUtils milvusSearchUtils) {
        this.milvusSearchUtils = milvusSearchUtils;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (userId == null || filteredWords == null) {
            throw new RuntimeException("MilvusContentRetriever requires user id and keywords");
        }
        QueryResp queryResp;
        try {
            if (filteredWords instanceof String ) {
                if (equalFlag) {
                    queryResp= milvusSearchUtils.filterByComeFromExact(userId, 0, filteredWords.toString());
                } else {
                    queryResp= milvusSearchUtils.filterByComeFromNotEqual(userId, 0, filteredWords.toString());
                }

            } else if (filteredWords instanceof List) {
                List<String> filteredWordsList = new ArrayList<>();
                ((List<?>) filteredWords).forEach(word -> filteredWordsList.add(word.toString()));
                queryResp= milvusSearchUtils.filterByComeFromIn(userId, 0, filteredWordsList);
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
