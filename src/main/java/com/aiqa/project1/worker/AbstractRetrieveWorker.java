package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.AsyncTaskExecutor;
import com.aiqa.project1.utils.RateLimiter;
import com.aiqa.project1.utils.TimeoutControl;
import com.aiqa.project1.utils.MilvusSearchUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 抽象检索Worker基类
 * 实现模板方法模式，提供检索操作的通用逻辑
 */
@Slf4j
public abstract class AbstractRetrieveWorker {

    protected final RabbitTemplate rabbitTemplate;
    protected final RedisTemplate<String, Object> redisTemplate;
    protected final OpenAiChatModel douBaoLite;
    protected final ObjectMapper objectMapper;
    protected final RedisStoreUtils redisStoreUtils;
    protected final AsyncTaskExecutor asyncTaskExecutor;
    protected final TimeoutControl timeoutControl;
    protected final RateLimiter rateLimiter;
    protected final MilvusSearchUtils milvusSearchUtils;

    protected AbstractRetrieveWorker(
            RabbitTemplate rabbitTemplate,
            RedisTemplate<String, Object> redisTemplate,
            OpenAiChatModel douBaoLite,
            ObjectMapper objectMapper,
            RedisStoreUtils redisStoreUtils,
            AsyncTaskExecutor asyncTaskExecutor,
            TimeoutControl timeoutControl,
            RateLimiter rateLimiter,
            MilvusSearchUtils milvusSearchUtils) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.douBaoLite = douBaoLite;
        this.objectMapper = objectMapper;
        this.redisStoreUtils = redisStoreUtils;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.timeoutControl = timeoutControl;
        this.rateLimiter = rateLimiter;
        this.milvusSearchUtils = milvusSearchUtils;
    }

    /**
     * 执行检索的模板方法
     * @param state 状态对象
     */
    public final void executeRetrieve(State state, String routingKey) {
        try {
            // 限流控制
            if (!rateLimiter.tryAcquire()) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }

            Integer userId = state.getUserId();
            Integer sessionId = state.getSessionId();
            String query = (state.getRetrievalQuery() == null) ? state.getQuery() : state.getRetrievalQuery();

                    // 提取关键词
            String keywords = extractKeywords(state);
            
            // 执行具体检索逻辑
            List<Content> retrievalInformation = performRetrieve(
                    userId,
                    (state.getRetrievalGlobalFlag()) ? -1 : sessionId,  // 如果用户未在搜索框添加文件，则全局检索
                    keywords,
                    Query.from(query));
            
            // 保存检索结果
            saveRetrievalResults(
                    userId,
                    sessionId,
                    state.getMemoryId(),
                    retrievalInformation,
                    routingKey.split("\\.")[0]
                    );
            
            // 发送消息到下一个节点
            sendToNextNode(state, routingKey);
            
        } catch (AmqpException e) {
            handleAmqpException(e, state);
        } finally {
            // 释放限流许可
            rateLimiter.release();
        }
    }

    /**
     * 提取关键词
     * @param state
     * @return 关键词
     */
    protected String extractKeywords(State state) {
        String query = (state.getRetrievalQuery() == null) ? state.getQuery() : state.getRetrievalQuery();
        try {
            String prompt = "你是一个关键词提取专家，请从以下文本中提取最重要的关键词，只返回关键词，不要其他内容：\n" + query;
            String keywords = timeoutControl.chatWithTimeout(douBaoLite, prompt);
            return keywords;
        } catch (Exception e) {
            log.error("提取关键词失败: {}", e.getMessage());
            return query; // 如果提取失败，使用原始查询
        }
    }

    /**
     * 执行具体的检索逻辑，子类必须实现
     * @param userId 用户ID
     * @param keywords 关键词
     * @param query 查询对象
     * @return 检索结果
     */
    protected abstract List<Content> performRetrieve(Integer userId, Integer sessionId, String keywords, Query query);

    /**
     * 保存检索结果到Redis
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param memoryId 内存ID
     * @param retrievalInformation 检索结果
     */
    protected void saveRetrievalResults(Integer userId, Integer sessionId, Integer memoryId, List<Content> retrievalInformation, String routingKey) {
        try {
            // 将Content转换为可序列化的Map
            List<Map<String, Object>> serializableResults = retrievalInformation.stream()
                    .map(content -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("text", content.textSegment().text());
                        map.put("metadata", content.metadata());
                        map.put("检索段落来自于：", content.textSegment().metadata().getString("come_from"));
                        map.put("使用的检索器名称", routingKey);
                        return map;
                    })
                    .toList();

            // 使用连接池管理器执行Redis操作
            redisStoreUtils.setRetrievalInfo(userId, sessionId, memoryId, "retrieval", Collections.singleton(serializableResults));
        } catch (Exception e) {
            log.error("保存检索结果失败: {}", e.getMessage());
            throw new RuntimeException("保存检索结果失败", e);
        }
    }

    /**
     * 发送消息到下一个节点
     * @param state 状态对象
     */
    protected void sendToNextNode(State state, String routingKey) {
        try {
            // 使用异步任务执行器发送消息
            asyncTaskExecutor.submit(() -> {
                rabbitTemplate.convertAndSend(
                        SystemConfig.GATHER_TOPIC,
                        routingKey,
                        state,
                        message -> {
                            message.getMessageProperties().setExpiration(String.valueOf(SystemConfig.MESSAGE_EXPIRATION_TIME));
                            return message;
                        }
                );
                return null;
            }).get(SystemConfig.RABBITMQ_SEND_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage());
            throw new RuntimeException("发送消息失败", e);
        }
    }

    /**
     * 处理AMQP异常
     * @param e 异常对象
     * @param state 状态对象
     */
    protected void handleAmqpException(AmqpException e, State state) {
        log.error("AMQP异常: {}", e.getMessage());
        // 可以在这里添加重试逻辑或死信队列处理
        throw new RuntimeException("消息发送失败", e);
    }

}
