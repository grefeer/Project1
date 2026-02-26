package com.aiqa.project1.pojo.nodes;

import com.aiqa.project1.pojo.tag.OrganizationTag;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 决策类型枚举：定义闭环中的决策分支
// 决策类型枚举：定义闭环中的决策分支
enum DecisionType {
    RETRIEVE,    // 需要检索（首次/重新检索）
    FINISH;      // 回答满足要求，结束闭环

    // 枚举转字符串（用于分支判断）
    @Override
    public String toString() {
        return this.name().toLowerCase(); // 统一转小写，避免大小写问题
    }

    // 字符串转枚举（用于状态反序列化）
    public static DecisionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return RETRIEVE; // 默认值
        }
        return switch (value.toLowerCase()) {
            case "finish" -> FINISH;
            case "retrieve" -> RETRIEVE;
            default -> RETRIEVE;
        };
    }
}

public class AgenticRAGState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA;

    static {
        Map<String, Channel<?>> map = new HashMap<>(Map.of(
                "query", Channels.base(() -> ""),
                "rewrite_query", Channels.base(() -> ""),
                "user_id", Channels.base(() -> 0),
                "session_id", Channels.base(() -> 0),
                "memory_id", Channels.base(() -> 0),
                "retrieval_info", Channels.base(ArrayList::new),
                "answer", Channels.base(() -> ""),
                "decision", Channels.base(DecisionType.RETRIEVE::toString), // 初始决策：检索
                "reflection", Channels.base(() -> ""), // 反思内容
                "tool_metadata_list", Channels.base(ArrayList::new)
        ));

        map.put("retrieval_global_flag", Channels.base(() -> false));
        map.put("retrieval_web_flag", Channels.base(() -> false));
        map.put("local_retrieval_flag", Channels.base(() -> true));
        map.put("history_chat_requirements", Channels.base(() -> false));
        map.put("tags", Channels.base(ArrayList::new));
        SCHEMA = map;
    }

    public AgenticRAGState(Map<String, Object> data) {
        super(data);
    }

    // Getters
    public String getQuery() { return this.<String>value("query").orElse(""); }
    public String getRewriteQuery() { return this.<String>value("rewrite_query").orElse(""); }
    public Integer getUserId() { return this.<Integer>value("user_id").orElse(0); }
    public Integer getSessionId() { return this.<Integer>value("session_id").orElse(0); }
    public Integer getMemoryId() { return this.<Integer>value("memory_id").orElse(0); }
    public List<Map<String, List<String>>> getRetrievalInfo() { return this.<List<Map<String, List<String>>>>value("retrieval_info").orElse(new ArrayList<>()); }
    public String getAnswer() { return this.<String>value("answer").orElse(""); }
    public String getDecision() { return this.<String>value("decision").orElse(DecisionType.RETRIEVE.toString()); }
    public String getReflection() { return this.<String>value("reflection").orElse(""); }
    public List<ToolMetaData1> getToolMetadataList() { return this.<List<ToolMetaData1>>value("tool_metadata_list").orElse(new ArrayList<>()); }
    public List<String> getThoughtProcess() { return this.<List<String>>value("thought_process").orElse(new ArrayList<>()); }

    public Boolean getRetrievalGlobalFlag() { return this.<Boolean>value("retrieval_global_flag").orElse(false); }
    public Boolean getRetrievalWebFlag() { return this.<Boolean>value("retrieval_web_flag").orElse(false); }
    public Boolean getLocalRetrievalFlag() { return this.<Boolean>value("local_retrieval_flag").orElse(true); }
    public Boolean getHistoryChatRequirements() { return this.<Boolean>value("history_chat_requirements").orElse(false); }
    public List<OrganizationTag> getTags() { return this.<List<OrganizationTag>>value("tags").orElse(new ArrayList<>()); }
}