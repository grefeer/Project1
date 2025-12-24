package com.aiqa.project1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class UploadExecutor {
    @Bean("documentUploadExecutor")
    public ThreadPoolTaskExecutor  documentUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 上传文件是IO密集型
        executor.setCorePoolSize(40);
        executor.setMaxPoolSize(80);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("document-upload-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);

        executor.initialize();

        return executor;
//        return new ThreadPoolExecutor(
//                40, // 核心线程数（根据CPU核心数调整）
//                80, // 最大线程数
//                60, // 空闲线程存活时间
//                TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1000), // 任务队列
//                new ThreadFactory() { // 自定义线程名，便于排查
//                    private final AtomicInteger counter = new AtomicInteger(1);
//                    @Override
//                    public Thread newThread(Runnable r) {
//                        return new Thread(r, "document-upload-executor-" + counter.getAndIncrement());
//                    }
//                },
//                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由主线程执行，避免任务丢失
//        );
    }

    @Bean("qaTaskExecutor")
    public ThreadPoolTaskExecutor  qaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("qa-task-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);

        executor.initialize();

        return executor;
    }
}
