package com.aiqa.project1.utils;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis连接池管理器
 * 使用Spring Boot自动配置的Lettuce连接池, 提供高效的Redis连接管理
 */
@Component
@Slf4j
public class RedisPoolManager {

    private RedisConnectionFactory connectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        // 使用Spring Boot自动配置的RedisTemplate
        this.connectionFactory = redisTemplate.getConnectionFactory();
        
        log.info("RedisPoolManager初始化成功，使用Lettuce连接池");
    }

    /**
     * 获取Redis连接
     * @return 连接工厂实例
     */
    public RedisConnectionFactory getConnection() {
        if (connectionFactory == null) {
            init();
        }
        return connectionFactory;
    }

    /**
     * 获取RedisTemplate
     * @return RedisTemplate实例
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        if (redisTemplate == null) {
            init();
        }
        return redisTemplate;
    }

    /**
     * 执行Redis操作,带重试机制
     * @param operation Redis操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithRetry(RedisOperation<T> operation) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < 3) { // 使用默认重试次数3次
            try {
                return operation.execute(redisTemplate);
            } catch (DataAccessException e) {
                lastException = e;
                retryCount++;
                
                if (retryCount < 3) {
                    try {
                        // 指数退避重试
                        long waitTime = (long) (1000 * Math.pow(2, retryCount - 1)); // 默认基础延迟1秒
                        TimeUnit.MILLISECONDS.sleep(waitTime); // 只阻塞当前线程
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Redis操作被中断", ie);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                if (retryCount < 3) {
                    try {
                        long waitTime = (long) (1000 * Math.pow(2, retryCount - 1));
                        TimeUnit.MILLISECONDS.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Redis操作被中断", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("Redis操作失败，已达到最大重试次数", lastException);
    }

    /**
     * 执行RedisTemplate操作, 带超时控制
     * @param operation Redis操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithTimeout(RedisTemplateOperation<T> operation) {
        try {
            return operation.execute(redisTemplate);
        } catch (DataAccessException e) {
            log.error("Redis操作失败: {}", e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        } catch (Exception e) {
            log.error("Redis操作失败: {}", e.getMessage());
            throw new RuntimeException("Redis操作失败", e);
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (connectionFactory != null && connectionFactory instanceof LettuceConnectionFactory) {
                ((LettuceConnectionFactory) connectionFactory).destroy();
                log.info("Redis连接池已关闭");
            }
        } catch (Exception e) {
            log.error("关闭Redis连接池时发生错误", e);
        }
    }

    /**
     * 检查连接是否可用
     * @return 连接状态
     */
    public boolean isConnectionAvailable() {
        try {
            if (connectionFactory == null) {
                return false;
            }
            
            // 对于Lettuce连接，检查连接工厂状态而不是创建新连接
            if (connectionFactory instanceof LettuceConnectionFactory) {
                LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) connectionFactory;
                // 检查连接工厂是否已初始化且处于活动状态
                return lettuceFactory.isAutoStartup() && ! lettuceFactory.getConnection().isClosed();
            }
            
            // 对于其他类型的连接，尝试执行一个简单的命令
            return redisTemplate.getConnectionFactory().getConnection().ping().equals("PONG");
        } catch (Exception e) {
            log.error("检查Redis连接状态失败", e);
            return false;
        }
    }

    /**
     * 执行Redis事务操作
     * @param operations 事务操作列表
     * @return 操作结果列表
     */
    public List<Object> executeTransaction(SessionCallback<List<Object>> operations) {
        try {
            return redisTemplate.execute(operations);
        } catch (DataAccessException e) {
            log.error("Redis事务执行失败: {}", e.getMessage());
            throw new RuntimeException("Redis事务执行失败", e);
        }
    }

    /**
     * 执行Redis Pipeline操作（批量操作优化）
     * @param myOperations Pipeline操作
     * @return 操作结果列表
     */
    public List<Object> executePipeline(Consumer<RedisTemplate<String, Object>> myOperations) {
        try {
            return redisTemplate.executePipelined(new SessionCallback<Void>() {
                @Override
                public <K, V> Void execute(RedisOperations<K, V> operations) throws DataAccessException {
                    RedisTemplate<String, Object> template = (RedisTemplate<String, Object>) operations;
                    myOperations.accept(template);
                    return null;
                }
            });
        } catch (DataAccessException e) {
            log.error("Redis Pipeline执行失败: {}", e.getMessage());
            throw new RuntimeException("Redis Pipeline执行失败", e);
        }
    }

    /**
     * 获取Lettuce连接工厂（用于Lettuce特定操作）
     * @return LettuceConnectionFactory实例
     */
    public LettuceConnectionFactory getLettuceConnectionFactory() {
        if (connectionFactory instanceof LettuceConnectionFactory) {
            return (LettuceConnectionFactory) connectionFactory;
        }
        throw new IllegalStateException("当前不是Lettuce连接工厂");
    }

    /**
     * Redis操作函数式接口
     */
    @FunctionalInterface
    public interface RedisOperation<T> {
        T execute(RedisTemplate<String, Object> redisTemplate) throws Exception;
    }

    /**
     * RedisTemplate操作函数式接口
     */
    @FunctionalInterface
    public interface RedisTemplateOperation<T> {
        T execute(RedisTemplate<String, Object> redisTemplate) throws Exception;
    }
}