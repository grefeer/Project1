package com.aiqa.project1.config;

import com.aiqa.project1.utils.ExpireKeyListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisExpireConfig {

    // 1. 定义监听容器
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        // 订阅「键过期事件」的频道（__keyevent@0__:expired 表示db0的过期事件）
        container.addMessageListener(expireListenerAdapter(), new ChannelTopic("__keyevent@0__:expired"));
        return container;
    }

    // 2. 绑定事件处理方法
    @Bean
    public MessageListenerAdapter expireListenerAdapter() {
        // 绑定到 ExpireKeyListener 的 onKeyExpired 方法
        return new MessageListenerAdapter(new ExpireKeyListener(), "onKeyExpired");
    }
}