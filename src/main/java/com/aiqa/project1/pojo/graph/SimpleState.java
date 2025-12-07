package com.aiqa.project1.pojo.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

// 为我们的图定义状态
class SimpleState extends AgentState {
    public static final String MESSAGES_KEY = "messages";

    // 定义状态的模式。
    // MESSAGES_KEY 将持有一个字符串列表，新消息将被追加到其中。
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new)
    );

    public SimpleState(Map<String, Object> initData) {
        super(initData);
    }

    public List<String> messages() {
        return this.<List<String>>value("messages")
                .orElse( List.of() );
    }
}