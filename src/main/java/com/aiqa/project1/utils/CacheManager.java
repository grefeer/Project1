package com.aiqa.project1.utils;

import com.aiqa.project1.config.SystemConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存管理器
 * 提供多级缓存支持，减少重复计算和外部API调用
 */
@Component
@Slf4j
public class CacheManager {

    @Autowired
    private RedisPoolManager redisPoolManager;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取缓存值，如果不存在则计算并缓存
     * @param key 缓存键
     * @param supplier 值提供者
     * @param expireTime 过期时间（毫秒）
     * @param <T> 值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier, long expireTime) {
        try {
            // 尝试从Redis获取缓存
            Object cachedValue = redisPoolManager.executeWithTimeout(redisTemplate ->
                redisTemplate.opsForValue().get(key)
            );
            
            if (cachedValue != null) {
                log.debug("缓存命中: {}", key);
                return (T) cachedValue;
            }
            
            // 缓存未命中，计算新值
            log.debug("缓存未命中，计算新值: {}", key);
            T value = supplier.get();
            
            // 存入缓存
            redisPoolManager.executeWithTimeout(redisTemplate -> {
                redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.MILLISECONDS);
                return null;
            });
            
            return value;
        } catch (Exception e) {
            log.error("缓存操作失败: {}", e.getMessage());
            // 缓存失败时直接计算返回
            return supplier.get();
        }
    }

    /**
     * 获取缓存值，使用默认过期时间
     * @param key 缓存键
     * @param supplier 值提供者
     * @param <T> 值类型
     * @return 缓存值
     */
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        return getOrCompute(key, supplier, SystemConfig.CACHE_DEFAULT_EXPIRE_TIME);
    }

    /**
     * 异步获取缓存值，如果不存在则计算并缓存
     * @param key 缓存键
     * @param supplier 值提供者
     * @param expireTime 过期时间（毫秒）
     * @param <T> 值类型
     * @return CompletableFuture包装的缓存值
     */
    public <T> java.util.concurrent.CompletableFuture<T> getOrComputeAsync(String key, Supplier<T> supplier, long expireTime) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> 
            getOrCompute(key, supplier, expireTime)
        );
    }

    /**
     * 异步获取缓存值，使用默认过期时间
     * @param key 缓存键
     * @param supplier 值提供者
     * @param <T> 值类型
     * @return CompletableFuture包装的缓存值
     */
    public <T> java.util.concurrent.CompletableFuture<T> getOrComputeAsync(String key, Supplier<T> supplier) {
        return getOrComputeAsync(key, supplier, SystemConfig.CACHE_DEFAULT_EXPIRE_TIME);
    }

    /**
     * 设置缓存值
     * @param key 缓存键
     * @param value 缓存值
     * @param expireTime 过期时间（毫秒）
     */
    public void set(String key, Object value, long expireTime) {
        try {
            redisPoolManager.executeWithTimeout(redisTemplate -> {
                redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.MILLISECONDS);
                return null;
            });
            log.debug("设置缓存: {}", key);
        } catch (Exception e) {
            log.error("设置缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 设置缓存值，使用默认过期时间
     * @param key 缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        set(key, value, SystemConfig.CACHE_DEFAULT_EXPIRE_TIME);
    }

    /**
     * 获取缓存值
     * @param key 缓存键
     * @param <T> 值类型
     * @return 缓存值，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            Object value = redisPoolManager.executeWithTimeout(redisTemplate -> 
                redisTemplate.opsForValue().get(key)
            );
            
            if (value != null) {
                log.debug("获取缓存命中: {}", key);
            } else {
                log.debug("获取缓存未命中: {}", key);
            }
            
            return (T) value;
        } catch (Exception e) {
            log.error("获取缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     * @param key 缓存键
     */
    public void delete(String key) {
        try {
            redisPoolManager.executeWithTimeout(redisTemplate -> {
                redisTemplate.delete(key);
                return null;
            });
            log.debug("删除缓存: {}", key);
        } catch (Exception e) {
            log.error("删除缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 检查缓存是否存在
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean exists = redisPoolManager.executeWithTimeout(redisTemplate -> 
                redisTemplate.hasKey(key)
            );
            return exists != null && exists;
        } catch (Exception e) {
            log.error("检查缓存存在性失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成查询缓存键
     * @param prefix 前缀
     * @param params 参数
     * @return 缓存键
     */
    public String generateQueryKey(String prefix, Object... params) {
        StringBuilder keyBuilder = new StringBuilder(prefix);
        for (Object param : params) {
            keyBuilder.append(":").append(param);
        }
        return keyBuilder.toString();
    }

    /**
     * 生成哈希缓存键
     * @param prefix 前缀
     * @param content 内容
     * @return 缓存键
     */
    public String generateHashKey(String prefix, String content) {
        try {
            // 使用内容的哈希值作为键的一部分，避免键过长
            String hash = Objects.hash(content) + "";
            return prefix + ":" + hash;
        } catch (Exception e) {
            log.error("生成哈希缓存键失败: {}", e.getMessage());
            return prefix + ":" + System.currentTimeMillis();
        }
    }
}
