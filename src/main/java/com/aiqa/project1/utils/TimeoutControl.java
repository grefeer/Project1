package com.aiqa.project1.utils;

import com.aiqa.project1.config.SystemConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 超时控制工具类
 * 提供带超时控制和缓存支持的任务执行
 */
@Component
public class TimeoutControl {
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Autowired
    private CacheManager cacheManager;
    
    /**
     * 执行AI聊天请求，带超时控制和缓存
     * @param chatModel AI聊天模型
     * @param prompt 提示词
     * @return AI回复
     * @throws RuntimeException 如果超时或执行出错
     */
    public String chatWithTimeout(OpenAiChatModel chatModel, String prompt) {
        // 尝试从缓存获取结果
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> chatModel.chat(prompt), executorService);
        try {
            return future.get(SystemConfig.AI_CHAT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("AI聊天请求超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI聊天请求被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("AI聊天请求执行失败", e);
            }
    }
    
    /**
     * 执行带超时的任务
     * @param task 要执行的任务
     * @param timeout 超时时间（毫秒）
     * @param <T> 返回类型
     * @return 任务结果
     * @throws RuntimeException 如果超时或执行出错
     */
    public <T> T executeWithTimeout(java.util.concurrent.Callable<T> task, long timeout) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
        
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("任务执行超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("任务执行被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("任务执行失败", e);
        }
    }
    
    /**
     * 执行带超时和缓存的任务
     * @param cacheKey 缓存键
     * @param task 要执行的任务
     * @param timeout 超时时间（毫秒）
     * @param expireTime 缓存过期时间（毫秒）
     * @param <T> 返回类型
     * @return 任务结果
     * @throws RuntimeException 如果超时或执行出错
     */
    public <T> T executeWithTimeoutAndCache(String cacheKey, java.util.concurrent.Callable<T> task, long timeout, long expireTime) {
        return cacheManager.getOrCompute(cacheKey, () -> executeWithTimeout(task, timeout), expireTime);
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}