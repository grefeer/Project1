package com.aiqa.project1.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.time.Duration;


@Component
public class RedisStoreUtils {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    public RedisStoreUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 设置需要检索的检索器数量
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param count
     * @return
     */
    public Boolean putRetrievalCount(Integer userId, Integer sessionId, Integer memoryId, Integer count) {
        return redisTemplate.opsForValue().setIfAbsent("retrievalCount:userId:%s:sessionId:%s:memoryId:%s".formatted(userId, sessionId, memoryId), count, DEFAULT_TTL);
    }

    /**
     * 未执行检索行为的检索器个数减一，标明又有一个检索器完成检索
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Long decreaseRetrievalCount(Integer userId, Integer sessionId, Integer memoryId) {
        Long var = redisTemplate.opsForValue().increment("retrievalCount:userId:%s:sessionId:%s:memoryId:%s".formatted(userId, sessionId, memoryId), -1);
        if (var != null && var.intValue() <= 0) {
            redisTemplate.opsForValue().getAndDelete("retrievalCount:userId:%s:sessionId:%s:memoryId:%s".formatted(userId, sessionId, memoryId));
        }
        return var;
    }

    /**
     * 获取未执行检索行为的检索器个数
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Integer getRetrievalCount(Integer userId, Integer sessionId, Integer memoryId) {
        return (Integer) redisTemplate.opsForValue().get("retrievalCount:userId:%s:sessionId:%s:memoryId:%s".formatted(userId, sessionId, memoryId));
    }

    /**
     * 存入检索信息
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param retrieverName
     * @param retrievalInfo
     */
    public void setRetrievalInfo(Integer userId, Integer sessionId, Integer memoryId, String retrieverName, Collection<Object> retrievalInfo) {
        redisTemplate.opsForList().leftPushAll("%s:userId:%s:sessionId:%s:memoryId:%s".formatted(retrieverName, userId, sessionId, memoryId), retrievalInfo);
    }

    /**
     * 取出所有检索信息
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param retrieverName
     * @return
     */
    public List<?> getRetrievalInfo(Integer userId, Integer sessionId, Integer memoryId, String retrieverName) {
        return  redisTemplate.opsForList().rightPop("%s:userId:%s:sessionId:%s:memoryId:%s".formatted(retrieverName, userId, sessionId, memoryId),1000);
    }


    /**
     * 存储对话历史
     * 每次 push 后刷新过期时间，保证活跃会话不丢失
     * @param userId
     * @param sessionId
     * @param memory
     */
    public Long setChatMemory(Integer userId, Integer sessionId,  String memory) {
        Long idx = redisTemplate.opsForList().rightPush("chatMemory:userId:%s:sessionId:%s".formatted(userId, sessionId), memory);
        redisTemplate.expire("chatMemory:userId:%s:sessionId:%s".formatted(userId, sessionId), DEFAULT_TTL);
        return idx;
    }


    /**
     * 获取最后一次对话
     * @param userId
     * @param sessionId
     * @return
     */
    public String getLastChatMemory(Integer userId, Integer sessionId) {
        return (String) redisTemplate.opsForList().getLast("chatMemory:userId:%s:sessionId:%s".formatted(userId, sessionId));
    }

    /**
     * 获取对话历史
     * 增加 limit 参数，防止 Prompt Token 溢出
     * 例如：limit=10 表示只获取最近 10 条
     * @param userId
     * @param sessionId
     * @param limit
     * @return
     */
    public List<?> getChatMemory(Integer userId, Integer sessionId, Integer limit) {
        String key = "chatMemory:userId:%s:sessionId:%s".formatted(userId, sessionId);
        // 直接使用负数索引：-limit 表示取最后 limit 个元素
        long start = (limit != null && limit > 0) ? -limit : 0;
        return redisTemplate.opsForList().range(key, start, -1);
    }

    public List<?> getAllChatMemory(Integer userId, Integer sessionId) {
        return getChatMemory(userId, sessionId, 20); // 默认给个保护限制，例如 20 条
    }

    public Long getChatMemoryCount(Object userId, Object sessionId) {
        String key = "chatMemory:userId:%s:sessionId:%s".formatted(userId, sessionId);
        return redisTemplate.opsForList().size(key);
    }

}
