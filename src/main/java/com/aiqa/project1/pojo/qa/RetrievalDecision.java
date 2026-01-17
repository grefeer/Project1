package com.aiqa.project1.pojo.qa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RetrievalDecision {
    @JsonProperty("related_documents")
    private List<String> relatedDocuments;

    @JsonProperty("web_retrieval_flag")
    private boolean webRetrievalFlag;

    @JsonProperty("additional_local_search_requirements")
    private boolean localRetrievalFlag;

    @JsonProperty("history_chat_requirements")
    private boolean historyChatRequirements;

    @Override
    public String toString() {
        return "RetrievalDecision{" +
                "relatedDocuments=" + relatedDocuments +
                ", webRetrievalFlag=" + webRetrievalFlag +
                '}';
    }
}