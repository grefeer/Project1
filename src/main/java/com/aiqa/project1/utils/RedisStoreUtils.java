package com.aiqa.project1.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStoreUtils {
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStoreUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean putRetrievalCount(Integer userId, Integer sessionId, Integer count) {
        return redisTemplate.opsForValue().setIfAbsent("retrievalCount:userId:%s:sessionId:%s".formatted(userId,sessionId), count);
    }

    public Long decreaseRetrievalCount(Integer userId, Integer sessionId) {
        return redisTemplate.opsForValue().decrement("retrievalCount:userId:%s:sessionId:%s".formatted(userId,sessionId));
    }

    public Integer getRetrievalCount(Integer userId, Integer sessionId) {
        return (Integer) redisTemplate.opsForValue().get("retrievalCount:userId:%s:sessionId:%s".formatted(userId,sessionId));
    }

    public void setRetrievalInfo(Integer userId, Integer sessionId,String retrieverName, String retrievalInfo) {
        redisTemplate.opsForValue().set("%s:userId:%s:sessionId:%s".formatted(retrieverName, userId, sessionId), retrievalInfo);
    }

    public void getRetrievalInfo(Integer userId, Integer sessionId,String retrieverName) {
        redisTemplate.opsForValue().get("%s:userId:%s:sessionId:%s".formatted(retrieverName, userId, sessionId));
    }


}
