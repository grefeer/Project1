package com.aiqa.project1.nodes;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 调用Tavily API实现
 */
@Component
public class WebSearchNode implements Node{
    private final ContentRetriever webSearchContentRetriever;

    public WebSearchNode(ContentRetriever webSearchContentRetriever) {
        this.webSearchContentRetriever = webSearchContentRetriever;
    }


    public State run(State state) {
        state.getRetrievalInfo().addAll(webSearchContentRetriever.retrieve(Query.from(state.getRetrievalQuery())));
        return state;
    }
}
