package com.aiqa.project1.utils;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryManager {

    // 存储用户ID与对话内存的映射（线程安全）
    private final Map<Integer, ChatMemory> userMemories = new ConcurrentHashMap<>();
    // 最大对话Token数（根据模型调整，如gpt-3.5-turbo建议2048）
    private static final int MAX_MESSAGES = 10;

    /**
     * 获取用户的对话内存，不存在则自动创建
     */
    public ChatMemory getChatMemory(Integer userId) {
        return userMemories.computeIfAbsent(userId, id ->
                MessageWindowChatMemory.builder()
                .maxMessages(MAX_MESSAGES)      // 超过该值自动修剪 oldest 消息
                .build()
        );
    }

    /**
     * 清除用户的对话历史（如用户主动重置对话）
     */
    public void clearChatMemory(Integer userId) {
        userMemories.remove(userId);
    }
}