package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.DeadLetterMapper;
import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.pojo.DeadLetter;
import com.aiqa.project1.pojo.nodes.State;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
@Slf4j
public class MysqlUpdateConsumer {
    private final SessionChatMapper sessionChatMapper;
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final CacheAsideUtils cacheAsideUtils;
    private final DeadLetterMapper deadLetterMapper;

    public MysqlUpdateConsumer(SessionChatMapper sessionChatMapper, RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, CacheAsideUtils cacheAsideUtils, DeadLetterMapper deadLetterMapper) {
        this.sessionChatMapper = sessionChatMapper;
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.cacheAsideUtils = cacheAsideUtils;
        this.deadLetterMapper = deadLetterMapper;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "mysql.chat.memory",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "50000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.mysql")
                    }
            ),
            exchange = @Exchange(value = "mysql.update", type = ExchangeTypes.DIRECT),
            key = "chat.memory"
    ))
    public void handleMysqlChatMemoryUpdate(State state, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
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
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("MySQL更新失败: {}", e.getMessage(), e);
            // 用params传递错误信息
            state.setParams(e.getMessage());
            channel.basicNack(tag, false, false);
        }
    }

    /**
     * 定义一个死信消费者，专门处理失败的消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "dlq.mysql", durable = "true"),
            exchange = @Exchange(value = "dlx.exchange", type = ExchangeTypes.TOPIC),
            key = "dlx.mysql"
    ))
    public void handleDeadLetter(State state,
                                 @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 这里可以记录日志、存入数据库报错表、或者发送报警通知
        System.err.println("收到死信消息: " + state);
        if (xDeath != null && !xDeath.isEmpty()) {
            Map<String, Object> entry = xDeath.getFirst();
            System.out.println("--- 死信详情 ---");
            System.out.println("原因: " + entry.get("reason"));      // rejected, expired, maxlen
            System.out.println("原始队列: " + entry.get("queue"));   // 消息从哪个队列来的
            System.out.println("发生时间: " + entry.get("time"));
            DeadLetter entity = new DeadLetter();
            entity.setCreatedTime(LocalDateTime.now());
            entity.setMessage(state.toString() + "发生错误，原因：" + entry.get("reason"));
            entity.setErrorFiled("mysql");
            entity.setUserId(state.getUserId());
            deadLetterMapper.insert(entity);
        }
    }
}