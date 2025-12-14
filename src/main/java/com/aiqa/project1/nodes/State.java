package com.aiqa.project1.nodes;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.content.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class State {
    private Integer userId;
    private ChatMemory chatMemory;
    private List<Content> retrievalInfo;
    private String query;
    private Integer maxReflection = 3;
    private Integer maxRetrievalCount = 1000;
    private String retrievalQuery;
}
