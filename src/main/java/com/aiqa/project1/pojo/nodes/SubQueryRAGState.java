package com.aiqa.project1.pojo.nodes;

import com.aiqa.project1.pojo.tag.OrganizationTag;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubQueryRAGState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA;

    static {
        Map<String, Channel<?>> map = new HashMap<>(Map.of(
                "query", Channels.base(() -> ""),
                "user_id", Channels.base(() -> 0),
                "session_id", Channels.base(() -> 0),
                "memory_id", Channels.base(() -> 0),
                "sub_query", Channels.base(ArrayList::new),
                "retrieval_info", Channels.base(ArrayList::new),
                "answer", Channels.base(() -> ""),
                "tool_metadata_list", Channels.base(ArrayList::new)
        ));
        map.put("tags", Channels.base(ArrayList::new));

        SCHEMA = map;
    }


    public SubQueryRAGState(Map<String, Object> data) {
        super(data);
    }

    // Getters
    public String getQuery() { return this.<String>value("query").orElse(""); }
    public Integer getUserId() { return this.<Integer>value("user_id").orElse(0); }
    public Integer getSessionId() { return this.<Integer>value("session_id").orElse(0); }
    public Integer getMemoryId() { return this.<Integer>value("memory_id").orElse(0); }
    public List<SubQuery1> getSubQuery() { return this.<List<SubQuery1>>value("sub_query").orElse(new ArrayList<>()); }
    public List<Map<String, List<String>>> getRetrievalInfo() { return this.<List<Map<String, List<String>>>>value("retrieval_info").orElse(new ArrayList<>()); }
    public List<ToolMetaData1> getToolMetadataList() { return this.<List<ToolMetaData1>>value("tool_metadata_list").orElse(new ArrayList<>()); }
    public String getAnswer() { return this.<String>value("answer").orElse(""); }
    public List<OrganizationTag> getTags() { return this.<List<OrganizationTag>>value("tags").orElse(new ArrayList<>()); }

}