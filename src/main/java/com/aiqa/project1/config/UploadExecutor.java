package com.aiqa.project1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class UploadExecutor {
    @Bean("uploadExecutor1")
    public ExecutorService  uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 上传文件是IO密集型
        executor.setCorePoolSize(40);
        executor.setMaxPoolSize(80);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("UploadExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setKeepAliveSeconds(60);

        executor.initialize();

        return executor.getThreadPoolExecutor();
    }
}
