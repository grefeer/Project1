package com.aiqa.project1.consumer;

import com.aiqa.project1.mapper.DeadLetterMapper;
import com.aiqa.project1.pojo.DeadLetter;
import com.aiqa.project1.pojo.nodes.*;
import com.rabbitmq.client.Channel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.BindException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class RagConsumer {
    private final DeadLetterMapper deadLetterMapper;
    CompiledGraph<NaiveRAGState> naiveRagStateCompiledGraph;
    CompiledGraph<SubQueryRAGState> subQueryRAGStateCompiledGraph;
    CompiledGraph<AgenticRAGState> agenticRagStateCompiledGraph;

    private final AgenticRAGGraph agenticRAGGraph;
    private final NaiveRAGGraph naiveRAGGraph;
    private final SubQueryRAGGraph subQueryRAGGraph;

    public RagConsumer(AgenticRAGGraph agenticRAGGraph, NaiveRAGGraph naiveRAGGraph, SubQueryRAGGraph subQueryRAGGraph, DeadLetterMapper deadLetterMapper) throws GraphStateException {
        this.agenticRAGGraph = agenticRAGGraph;
        this.naiveRAGGraph = naiveRAGGraph;
        this.subQueryRAGGraph = subQueryRAGGraph;

        this.agenticRagStateCompiledGraph = agenticRAGGraph.buildGraph();
        this.naiveRagStateCompiledGraph = naiveRAGGraph.buildGraph();
        this.subQueryRAGStateCompiledGraph = subQueryRAGGraph.buildGraph();
        this.deadLetterMapper = deadLetterMapper;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "rag.agentic.queue",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "50000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.rag.agentic")
                    }
            ),
            exchange = @Exchange(value = "rag.chat", type = ExchangeTypes.DIRECT),
            key = "rag.agentic"
    ))
    public void agenticRagRun(Map<String, Object> initialStateData, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // 测试Error
            throw new Exception("测试");
//            agenticRagStateCompiledGraph.invoke(initialStateData);
//            channel.basicAck(tag, false);
        } catch (Exception e) {
            e.printStackTrace();
//            initialStateData.put("dead_message", e.getMessage());
            channel.basicNack(tag, false, false);
//            throw new AmqpRejectAndDontRequeueException(e);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "rag.naive.queue",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "50000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.rag.naive")
                    }
            ),
            exchange = @Exchange(value = "rag.chat", type = ExchangeTypes.DIRECT),
            key = "rag.naive"
    ))
    public void naiveRagRun(Map<String, Object> initialStateData, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            naiveRagStateCompiledGraph.invoke(initialStateData);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            e.printStackTrace();
//            initialStateData.put("dead_message", e.getMessage());
            channel.basicNack(tag, false, false);
//            throw new AmqpRejectAndDontRequeueException(e);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "rag.subquery.queue",
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-message-ttl", value = "50000", type = "java.lang.Integer"),
                            // 绑定死信交换机
                            @Argument(name = "x-dead-letter-exchange", value = "dlx.exchange"),
                            // 绑定死信路由键（消息变成死信后，发送给 DLX 时使用的 key）
                            @Argument(name = "x-dead-letter-routing-key", value = "dlx.rag.subquery")
                    }
            ),
            exchange = @Exchange(value = "rag.chat", type = ExchangeTypes.DIRECT),
            key = "rag.subquery"
    ))
    public void subqueryRagRun(Map<String, Object> initialStateData, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            subQueryRAGStateCompiledGraph.invoke(initialStateData);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            e.printStackTrace();
//            initialStateData.put("dead_message", e.getMessage());
            channel.basicNack(tag, false, false);
//            throw new AmqpRejectAndDontRequeueException(e);
        }
    }

    /**
     * 定义一个死信消费者，专门处理失败的消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "dlq.rag.queue", durable = "true"),
            exchange = @Exchange(value = "dlx.exchange", type = ExchangeTypes.TOPIC),
            key = "dlx.rag.#"
    ))
    public void handleDeadLetter(Map<String, Object> failedData,
                                 @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 这里可以记录日志、存入数据库报错表、或者发送报警通知
        System.err.println("收到死信消息: " + failedData);

        if (xDeath != null && !xDeath.isEmpty()) {
            Map<String, Object> entry = xDeath.getFirst();
            System.out.println("--- 死信详情 ---");
            System.out.println("原因: " + entry.get("reason"));      // rejected, expired, maxlen
            System.out.println("原始队列: " + entry.get("queue"));   // 消息从哪个队列来的
            System.out.println("发生时间: " + entry.get("time"));

            DeadLetter entity = new DeadLetter();
            entity.setCreatedTime(LocalDateTime.now());
            entity.setMessage(failedData.toString() + "发生错误，原因："+ (String) entry.get("reason"));
            entity.setErrorFiled("rag");
            entity.setUserId(Integer.parseInt(failedData.get("user_id").toString()));
            deadLetterMapper.insert(entity);
        }


    }

}
