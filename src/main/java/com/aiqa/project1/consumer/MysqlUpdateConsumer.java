package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Component
@Slf4j
public class MysqlUpdateConsumer {
    private final SessionChatMapper sessionChatMapper;
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final CacheAsideUtils cacheAsideUtils;

    public MysqlUpdateConsumer(SessionChatMapper sessionChatMapper, RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, CacheAsideUtils cacheAsideUtils) {
        this.sessionChatMapper = sessionChatMapper;
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.cacheAsideUtils = cacheAsideUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "mysql.chat.memory", durable = "true"),
            exchange = @Exchange(value = "mysql.update", type = ExchangeTypes.DIRECT),
            key = "chat.memory"
    ))
    public void handleMysqlChatMemoryUpdate(State state) {
        Integer userId = state.getUserId();
        Integer sessionId = state.getSessionId();
        try {
            // 🌟 修复 1：绕过 cacheAsideUtils 的限制，直接获取全部 Redis 记录
            // 注意：RedisStoreUtils.getChatMemory 传 limit 为很大或重写一个获取全部的逻辑
            // 假设你在 redisStoreUtils 加了一个 getAllChatMemoryList 的方法，或者传 -1 获取全部
            List<Object> rawList = redisStoreUtils.getChatMemory(userId, sessionId, -1);
            if (rawList == null || rawList.isEmpty()) return;

            List<String> chatMemoryList = rawList.stream().map(Object::toString).toList();

            QueryWrapper<UserChatMemory> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId).eq("session_id", sessionId);

            int currentMemoryCount = userChatMemoryMapper.selectCount(wrapper).intValue();

            if (chatMemoryList.size() < currentMemoryCount) {
                log.warn("Redis数据({})少于MySQL({}), 可能发生数据丢失或被截断", chatMemoryList.size(), currentMemoryCount);
                return; // 不要抛出异常中断，这里可能由于 Redis 过期导致，直接 return 即可
            } else if (chatMemoryList.size() == currentMemoryCount) {
                return;
            }

            // 🌟 修复 2：修正索引逻辑，直接使用 i
            List<UserChatMemory> toInsert = IntStream.range(currentMemoryCount, chatMemoryList.size())
                    .mapToObj(i -> new UserChatMemory(
                            null,
                            userId,
                            sessionId,
                            i + 1,
                            chatMemoryList.get(i), // 修复：原来是 get(i - currentMemoryCount)
                            LocalDateTime.now(),
                            0
                    ))
                    .toList();
            userChatMemoryMapper.insertOrUpdate(toInsert);
            log.info("成功同步 {} 条聊天记录到 MySQL", toInsert.size());

        } catch (Exception e) {
            log.error("MySQL更新失败: {}", e.getMessage(), e);
        }
    }
}