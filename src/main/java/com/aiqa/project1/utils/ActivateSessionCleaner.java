package com.aiqa.project1.utils;

import com.aiqa.project1.mapper.UserChatMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Slf4j
public class ActivateSessionCleaner {

    // 注入依赖（根据你的实际类调整）
    private final RedisPoolManager redisPoolManager;
    private final RedisStoreUtils redisStoreUtils;

    public ActivateSessionCleaner(RedisPoolManager redisPoolManager, UserChatMemoryMapper userChatMemoryMapper, RedisStoreUtils redisStoreUtils) {
        this.redisPoolManager = redisPoolManager;
        this.redisStoreUtils = redisStoreUtils;
    }

    /**
     * 定时删除 USER_ACTIVATE_SESSION 中过期的 Session ID
     */
    @Scheduled(cron = "0 0/5 * * * ?", scheduler = "threadPoolTaskScheduler")
    @Transactional(rollbackFor = Exception.class) // MySQL操作加事务
    public void deleteActivateSessionId() {
        // 步骤1：获取所有目标 Redis Key（activateSession:userId:*）
        Set<String> allKeys = redisStoreUtils.getAllKeysByScan("activateSession:userId:*");
        if (allKeys.isEmpty()) {
            // 无数据直接返回，减少无效操作
            return;
        }
        // 步骤2：批量处理每个用户的过期Session
        for (String key_ : allKeys) {
            try {
                String userId = key_.replace("activateSession:userId:", "");
                redisStoreUtils.deleteActivateSessionId(Integer.valueOf(userId));
            } catch (Exception e) {
                // 单个用户处理失败不影响整体，记录日志
                log.error("处理用户过期Session失败，key:{}", key_, e);
            }
        }
    }



}

