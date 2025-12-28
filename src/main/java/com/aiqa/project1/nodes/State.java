package com.aiqa.project1.nodes;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.content.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class State {
    private Integer userId;
    private Integer sessionId;
    private Integer memoryId;
    private ChatMemory chatMemory;
    private List<Content> retrievalInfo;
    private String query;
    private Integer maxReflection = 3;
    private Integer maxRetrievalCount = 1000;
    private Integer maxSubtasksCount = 1000;
    private String retrievalQuery;
    private Boolean retrievalDBFlag = true;
    private Boolean retrievalGlobalFlag = false;
    private String params = "";

    public State(Integer userId, Integer sessionId, Integer memoryId, String query) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.query = query;
    }

    public State(Integer userId, Integer sessionId, Integer memoryId, String query, String retrievalQuery) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.query = query;
        this.retrievalQuery = retrievalQuery;
    }

    public State(Integer userId, Integer sessionId, Integer memoryId, ChatMemory chatMemory, List<Content> retrievalInfo, String query, Integer maxReflection, Integer maxRetrievalCount, String retrievalQuery, Boolean retrievalDBFlag, Boolean retrievalGlobalFlag) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.chatMemory = chatMemory;
        this.retrievalInfo = retrievalInfo;
        this.query = query;
        this.maxReflection = maxReflection;
        this.maxRetrievalCount = maxRetrievalCount;
        this.retrievalQuery = retrievalQuery;
        this.retrievalDBFlag = retrievalDBFlag;
        this.retrievalGlobalFlag = retrievalGlobalFlag;
    }
}
