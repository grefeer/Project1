package com.aiqa.project1.utils;

import com.aiqa.project1.config.SystemConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 系统限流器，控制并发请求数量
 */
@Component
public class RateLimiter {
    
    private final Semaphore semaphore;
    
    public RateLimiter() {
        this.semaphore = new Semaphore(SystemConfig.MAX_CONCURRENT_REQUESTS);
    }
    
    /**
     * 获取执行许可
     * @return 是否成功获取许可
     */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }
    
    /**
     * 获取执行许可，带超时
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否成功获取许可
     * @throws InterruptedException 如果线程被中断
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return semaphore.tryAcquire(timeout, unit);
    }
    
    /**
     * 获取执行许可，如果无法获取则阻塞直到获取
     * @throws InterruptedException 如果线程被中断
     */
    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }
    
    /**
     * 释放执行许可
     */
    public void release() {
        semaphore.release();
    }
    
    /**
     * 获取当前可用的许可数量
     * @return 可用许可数量
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}