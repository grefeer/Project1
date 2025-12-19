package com.aiqa.project1.service.impl;


import com.aiqa.project1.worker.StateProducer;

import org.springframework.stereotype.Service;


@Service
public class QuestionAnsweringService {
    private final StateProducer stateProducer;

    public QuestionAnsweringService(StateProducer stateProducer) {
        this.stateProducer = stateProducer;
    }

    public void answerQuestion(Integer userId, Integer sessionId, Integer memoryId, String query) {
        stateProducer.run(userId, sessionId, memoryId, query);
    }

    private String buildCacheKey(Integer userId, String query, String chatMemory) {
        // 简单的缓存键生成策略，可根据实际需求调整
        return userId + ":" + query.hashCode() + ":" + chatMemory.hashCode();
    }
}