package com.aiqa.project1.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 异步任务执行器
 * 提供高效的异步任务执行能力，包括线程池管理、任务超时控制和结果处理
 */
@Component
public class AsyncTaskExecutor {

    @Qualifier("documentUploadExecutor")
    @Autowired
    private ThreadPoolTaskExecutor documentUploadExecutor;
    
    @Qualifier("threadPoolTaskScheduler")
    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;


    /**
     * 异步执行任务
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return CompletableFuture包装的结果
     */
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, documentUploadExecutor);
    }

    /**
     * 异步执行任务，带超时控制
     * @param task 要执行的任务
     * @param timeout 超时时间（毫秒）
     * @param <T> 返回类型
     * @return CompletableFuture包装的结果
     */
    public <T> CompletableFuture<T> submitWithTimeout(Supplier<T> task, long timeout) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(task, documentUploadExecutor);
        
        // 设置超时
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        threadPoolTaskScheduler.schedule(() -> {
            if (!future.isDone()) {
                future.cancel(true);
                timeoutFuture.completeExceptionally(new TimeoutException("任务执行超时"));
            }
        }, Instant.now().plusMillis(timeout));

        return future.applyToEither(timeoutFuture, result -> result);
    }

    /**
     * 批量异步执行任务
     * @param tasks 任务列表
     * @param <T> 返回类型
     * @return CompletableFuture包装的结果列表
     */
    public <T> CompletableFuture<Void> submitAll(Iterable<Supplier<T>> tasks) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[0];
        
        for (Supplier<T> task : tasks) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(task, documentUploadExecutor);
            futures = Arrays.copyOf(futures, futures.length + 1);
            futures[futures.length - 1] = future;
        }
        
        return CompletableFuture.allOf(futures);
    }

    /**
     * 批量异步执行任务，带超时控制
     * @param tasks 任务列表
     * @param timeout 每个任务的超时时间（毫秒）
     * @param <T> 返回类型
     * @return CompletableFuture包装的结果列表
     */
    public <T> CompletableFuture<Void> submitAllWithTimeout(Iterable<Supplier<T>> tasks, long timeout) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[0];
        
        for (Supplier<T> task : tasks) {
            CompletableFuture<T> future = submitWithTimeout(task, timeout);
            futures = Arrays.copyOf(futures, futures.length + 1);
            futures[futures.length - 1] = future;
        }
        
        return CompletableFuture.allOf(futures);
    }

    /**
     * 延迟执行任务
     * @param task 要执行的任务
     * @param delay 延迟时间（毫秒）
     * @param <T> 返回类型
     * @return CompletableFuture包装的结果
     */
    public <T> CompletableFuture<T> schedule(Supplier<T> task, long delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        threadPoolTaskScheduler.schedule(() -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        },Instant.now().plusMillis(delay));
        
        return future;
    }

    /**
     * 批量异步执行任务，并返回包含所有结果的CompletableFuture
     * @param tasks 任务列表
     * @param <T> 返回类型
     * @return CompletableFuture<List<T>> 所有任务的结果列表
     */
    public <T> CompletableFuture<List<T>> submitAllWithResult(Iterable<Supplier<T>> tasks) {
        // 收集每个任务的CompletableFuture
        List<CompletableFuture<T>> futureList = new ArrayList<>();
        for (Supplier<T> task : tasks) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(task, documentUploadExecutor);
            futureList.add(future);
        }

        // 等待所有任务完成后，汇总结果
        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .thenApply(v -> futureList.stream()
                        .map(future -> {
                            try {
                                return future.get();
                            } catch (InterruptedException | ExecutionException e) {
                                // 异常处理逻辑，可根据业务调整
                                throw new CompletionException(e);
                            }
                        })
                        .collect(Collectors.toList()));
    }

    /**
     * 周期性执行任务
     * @param task 要执行的任务
     * @param initialDelay 初始延迟时间（毫秒）
     * @param period 执行周期（毫秒）
     * @return ScheduledFuture，可用于取消任务
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
        return threadPoolTaskScheduler.scheduleAtFixedRate(task, Instant.now().plusMillis(initialDelay), Duration.ofMillis(period));
    }

    /**
     * 获取当前活跃线程数
     * @return 活跃线程数
     */
    public int getActiveCount() {
        return documentUploadExecutor.getActiveCount();
    }

    /**
     * 获取当前队列大小
     * @return 队列大小
     */
    public int getQueueSize() {
        return documentUploadExecutor.getQueueSize();
    }

    /**
     * 获取当前已完成的任务数
     * @return 已完成的任务数
     */
    public long getCompletedTaskCount() {
        return documentUploadExecutor.getThreadPoolExecutor().getCompletedTaskCount();
    }

    /**
     * 关闭执行器
     */
    @PreDestroy
    public void shutdown() {
        documentUploadExecutor.shutdown();
        threadPoolTaskScheduler.shutdown();

        try {
            if (!documentUploadExecutor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                documentUploadExecutor.getThreadPoolExecutor().shutdownNow();
            }
            if (!threadPoolTaskScheduler.getScheduledExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                threadPoolTaskScheduler.getScheduledExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
            documentUploadExecutor.getThreadPoolExecutor().shutdownNow();
            threadPoolTaskScheduler.getScheduledExecutor().shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

