package com.aiqa.project1.newworker;

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

            List<String> chatMemoryList = cacheAsideUtils.getChatMemory(userId, sessionId);
            if (chatMemoryList == null || chatMemoryList.isEmpty()) return;
            System.out.println("将redis中缓存的消息保存到mysql中");
            chatMemoryList.forEach(System.out::println);
            // 根据redis更新mysql
            QueryWrapper<UserChatMemory> wrapper = new QueryWrapper<>();
            wrapper.eq("user_id", userId).eq("session_id", sessionId);

            int currentMemoryCount = userChatMemoryMapper.selectCount(wrapper).intValue();

            if (chatMemoryList.size() < currentMemoryCount) throw new RuntimeException("redis的数据没有MySQL里的多，可能是redis初始化错误，或其他原因");
            else if (chatMemoryList.size() == currentMemoryCount) return;
            // 只有新数据才存入mysql中
            List<UserChatMemory> toInsert = IntStream.range(currentMemoryCount, chatMemoryList.size())
                    .mapToObj(i -> new UserChatMemory(
                            null,
                            userId,
                            sessionId,
                            i + 1,
                            chatMemoryList.get(i - currentMemoryCount).toString(),
                            LocalDateTime.now(),
                            0
                    ))
                    .toList();
            userChatMemoryMapper.insertOrUpdate(toInsert);
        } catch (Exception e) {
            // 记录错误，可重试或放入死信队列
            log.error("MySQL更新失败: {}", e.getMessage());
        }
    }


}