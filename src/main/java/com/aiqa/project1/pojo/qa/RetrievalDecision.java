package com.aiqa.project1.pojo.qa;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class RetrievalDecision {
    @JsonProperty("related_documents")
    private List<String> relatedDocuments;

    @JsonProperty("web_retrieval_flag")
    private boolean webRetrievalFlag;

    @JsonProperty("local_retrieval_flag")
    private boolean localRetrievalFlag;

    // Getters and Setters
    public List<String> getRelatedDocuments() {
        return relatedDocuments;
    }

    public void setRelatedDocuments(List<String> relatedDocuments) {
        this.relatedDocuments = relatedDocuments;
    }

    public boolean isWebRetrievalFlag() {
        return webRetrievalFlag;
    }

    public void setWebRetrievalFlag(boolean webRetrievalFlag) {
        this.webRetrievalFlag = webRetrievalFlag;
    }

    public boolean isLocalRetrievalFlag() {
        return localRetrievalFlag;
    }

    public void setLocalRetrievalFlag(boolean localRetrievalFlag) {
        this.localRetrievalFlag = localRetrievalFlag;
    }

    @Override
    public String toString() {
        return "RetrievalDecision{" +
                "relatedDocuments=" + relatedDocuments +
                ", webRetrievalFlag=" + webRetrievalFlag +
                '}';
    }
}