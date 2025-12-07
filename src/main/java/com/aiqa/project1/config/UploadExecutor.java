package com.aiqa.project1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class UploadExecutor {
    @Bean("uploadExecutor1")
    public ExecutorService  uploadExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        // 上传文件是IO密集型
//        executor.setCorePoolSize(40);
//        executor.setMaxPoolSize(80);
//        executor.setQueueCapacity(100);
//        executor.setThreadNamePrefix("UploadExecutor-");
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
//        executor.setKeepAliveSeconds(60);
//
//        executor.initialize();
//
//        return executor.getThreadPoolExecutor();
        return new ThreadPoolExecutor(
                20, // 核心线程数（根据CPU核心数调整）
                40, // 最大线程数
                60, // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), // 任务队列
                new ThreadFactory() { // 自定义线程名，便于排查
                    private final AtomicInteger counter = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "upload-executor-" + counter.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由主线程执行，避免任务丢失
        );
    }
}
