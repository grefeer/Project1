package com.aiqa.project1.nodes;


import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.content.Content;

import java.util.List;

public interface Node {
    public State run(State state);
}
