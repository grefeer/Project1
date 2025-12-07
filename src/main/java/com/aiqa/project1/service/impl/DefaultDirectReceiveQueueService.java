package com.aiqa.project1.service.impl;
import com.aiqa.project1.config.RabbitConfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @program: chain
 * @description: 默认 direct 队列接收组件
 * @author: Kenny.Qiu
 * @create: 2024/10/12 10:04
 */
@Slf4j
@Component

public class DefaultDirectReceiveQueueService {

    /**
     * 默认 direct 队列接收消息
     *
     * @param message 消息内容
     */
    @RabbitHandler
    @RabbitListener(queues = RabbitConfig.DEFAULT_QUEUE)
    public void messageReceive1(String message) {
        log.info("默认 {} 队列接收消息：{}", RabbitConfig.DEFAULT_QUEUE, message);
    }

    @RabbitHandler
    @RabbitListener(queues = "hello.queue2")
    public void messageReceive2(String message) {
        log.info("默认 {} 队列接收消息：{}", "hello.queue2", message);
    }

    @RabbitHandler
    @RabbitListener(queues = "haha.queue1")
    public void messageReceive3(String message) {
        log.info("默认 {} 队列接收消息：{}", "haha.queue1", message);
    }


}

