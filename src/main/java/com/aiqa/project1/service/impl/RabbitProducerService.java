package com.aiqa.project1.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * @className: RabbitProducerService
 * @program: chain
 * @description: RabbitMQ 生产者 Service 组件
 * @author: kenny
 * @create: 2024-10-04 01:11
 * @version: 1.0.0
 */
@Slf4j
@Service
public class RabbitProducerService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 向动态创建的队列发送消息
     *
     * @param queueName 队列名称
     * @param message   消息内容
     */
    public void sendMessageToQueue(String queueName, String message) {
        log.info("向队列：{}，发送消息：{}", queueName, message);
        rabbitTemplate.convertAndSend(queueName, message);
    }

    /**
     * 向动态创建的交换机发送消息
     *
     * @param exchangeName 交换机名称
     * @param routingKey   路由键
     * @param message      消息内容
     */
    public void sendMessageToExchange(String exchangeName, String routingKey, String message) {
        log.info("向交换机：{}，路由键：{}，发送消息：{}", exchangeName, routingKey, message);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message);
    }
}

