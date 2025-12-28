package com.aiqa.project1.worker;

import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.sql.Wrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class ResultConsumer {
    private final RedisStoreUtils redisStoreUtils;

    private final UserChatMemoryMapper userChatMemoryMapper;
    public ResultConsumer(RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper) {
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
    }

    @RabbitListener(queuesToDeclare = @Queue(value = "result", durable = "true"))
    public void receive(State state) {
        System.out.println(state);

        Integer userId = state.getUserId();
        Integer sessionId = state.getSessionId();
        String answer = redisStoreUtils.getLastChatMemory(userId, sessionId);
        System.out.println(answer);
        redisStoreUtils.setChatMemory(userId, sessionId,"<最终回答>" + answer);

        List<?> chatMemoryList = redisStoreUtils.getChatMemory(userId, sessionId, 10000);
        if (chatMemoryList == null || chatMemoryList.isEmpty()) return;
        System.out.println("111111111111111111");
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

        // 更新前活跃的 Session ID的时间
        redisStoreUtils.setOrIncreaseActivateSessionId(userId, sessionId);

    }
}
