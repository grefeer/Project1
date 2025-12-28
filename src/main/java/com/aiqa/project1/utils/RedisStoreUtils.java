package com.aiqa.project1.utils;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.time.Duration;
import java.util.stream.Collectors;


@Component
@Slf4j
public class RedisStoreUtils {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisPoolManager redisPoolManager;
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");
    private static final Double ONE_HOUR = toDoubleSeconds(LocalDateTime.now().plusHours(1)) - toDoubleSeconds(LocalDateTime.now());
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;

    public RedisStoreUtils(RedisTemplate<String, Object> redisTemplate, RedisPoolManager redisPoolManager, UserChatMemoryMapper userChatMemoryMapper, @Qualifier("threadPoolTaskScheduler") ThreadPoolTaskScheduler threadPoolTaskScheduler) {
        this.redisTemplate = redisTemplate;
        this.redisPoolManager = redisPoolManager;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
    }

    /**
     * 设置需要检索的检索器数量（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param count
     * @return
     */
    public Boolean putRetrievalCount(Integer userId, Integer sessionId, Integer memoryId, String subTask, Integer count) {
        String key = SystemConfig.REDIS_RETRIEVAL_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId, subTask);
        return redisPoolManager.executeWithRetry(template -> 
            template.opsForValue().setIfAbsent(key, count, DEFAULT_TTL));
    }

    /**
     * 未执行检索行为的检索器个数减一，标明又有一个检索器完成检索（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Long decreaseRetrievalCount(Integer userId, Integer sessionId, Integer memoryId, String subTask) {
        String key = SystemConfig.REDIS_RETRIEVAL_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId, subTask);
        return redisPoolManager.executeWithRetry(template -> {
            Long var = template.opsForValue().increment(key, -1);
            if (var != null && var.intValue() <= 0) {
                template.opsForValue().getAndDelete(key);
            }
            return var;
        });
    }

    /**
     * 获取未执行检索行为的检索器个数（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Integer getRetrievalCount(Integer userId, Integer sessionId, Integer memoryId, String subTask) {
        String key = SystemConfig.REDIS_RETRIEVAL_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId, subTask);
        return redisPoolManager.executeWithRetry(template ->
                (Integer) template.opsForValue().get(key));
    }


    /**
     * 获取未执行子查询的检索器个数（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Integer getSubtaskCount(Integer userId, Integer sessionId, Integer memoryId) {
        String key = SystemConfig.REDIS_SUBTASKS_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId);
        return redisPoolManager.executeWithRetry(template -> 
            (Integer) template.opsForValue().get(key));
    }

    /**
     * 设置需要检索的子查询数量（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param count
     * @return
     */
    public Boolean putSubtaskCount(Integer userId, Integer sessionId, Integer memoryId, Integer count) {
        String key = SystemConfig.REDIS_SUBTASKS_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId);
        return redisPoolManager.executeWithRetry(template ->
                template.opsForValue().setIfAbsent(key, count, DEFAULT_TTL));
    }

    /**
     * 未执行检索行为的子查询个数减一，标明又有一个子查询完成查询（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Long decreaseSubtaskCount(Integer userId, Integer sessionId, Integer memoryId) {
        String key = SystemConfig.REDIS_SUBTASKS_COUNT_KEY_FORMAT.formatted(userId, sessionId, memoryId);
        return redisPoolManager.executeWithRetry(template -> {
            Long var = template.opsForValue().increment(key, -1);
            template.expire(key, DEFAULT_TTL);
            return var;
        });
    }



    /**
     * 存入检索信息（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param retrieverName
     * @param retrievalInfo
     */
    public void setRetrievalInfo(Integer userId, Integer sessionId, Integer memoryId, String retrieverName, Collection<Object> retrievalInfo) {
        String key = SystemConfig.REDIS_RETRIEVAL_KEY_FORMAT.formatted(userId, sessionId, memoryId);
        redisPoolManager.executeWithRetry(template -> {
            template.opsForList().leftPushAll(key, retrievalInfo);
            return null;
        });
    }

    /**
     * 取出所有检索信息（带重试机制）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param retrieverName
     * @return
     */
    public List<?> getRetrievalInfo(Integer userId, Integer sessionId, Integer memoryId, String retrieverName) {
        String key = SystemConfig.REDIS_RETRIEVAL_KEY_FORMAT.formatted(userId, sessionId, memoryId);
        return redisPoolManager.executeWithRetry(template ->
            template.opsForList().rightPop(key, 1000));
    }

    /**
     * 批量获取多个检索器的信息（使用Pipeline优化）
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param retrieverNames 检索器名称列表
     * @return 检索器名称到检索信息的映射
     */
    public Map<String, List<?>> batchGetRetrievalInfo(Integer userId, Integer sessionId, Integer memoryId, List<String> retrieverNames) {
        Map<String, List<?>> result = new HashMap<>();
        
        try {
            List<Object> pipelineResults = redisPoolManager.executePipeline(template -> {
                for (String retrieverName : retrieverNames) {
                    String key = "%s:userId:%s:sessionId:%s:memoryId:%s".formatted(retrieverName, userId, sessionId, memoryId);
                    template.opsForList().rightPop(key, 1000);
                }
            });
            
            // 将结果与检索器名称对应
            for (int i = 0; i < retrieverNames.size() && i < pipelineResults.size(); i++) {
                result.put(retrieverNames.get(i), (List<?>) pipelineResults.get(i));
            }
        } catch (Exception e) {
            log.error("批量获取检索信息失败，回退到单个获取", e);
            // 回退到单个获取
            for (String retrieverName : retrieverNames) {
                result.put(retrieverName, getRetrievalInfo(userId, sessionId, memoryId, retrieverName));
            }
        }
        
        return result;
    }


    /**
     * 存储对话历史（带重试机制）
     * 每次 push 后刷新过期时间，保证活跃会话不丢失
     * @param userId
     * @param sessionId
     * @param memory
     */
    public Long setChatMemory(Integer userId, Integer sessionId,  String memory) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        return redisPoolManager.executeWithRetry(template -> {
            Long idx = template.opsForList().rightPush(key, memory);
            template.expire(key, DEFAULT_TTL);
            return idx;
        });
    }

    /**
     * 用memoryId删除对话
     * @param userId
     * @param sessionId
     * @param memoryId
     * @return
     */
    public Boolean deleteChatMemory(Integer userId, Integer sessionId, Integer memoryId) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        String deletedContent = String.format("<编号%d对话已删除>", memoryId);

        return redisPoolManager.executeWithRetry(template -> {
            template.expire(key, DEFAULT_TTL);
            List<Object> memory = template.opsForList().range(key, memoryId, memoryId);
            if (memory == null || memory.isEmpty()) return false;
            try {
                template.opsForList().set(key, memoryId, deletedContent);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        });
    }

    /**
     * 指定内容删除对话
     * @param userId
     * @param sessionId
     * @param memory
     * @return
     */
    public Boolean removeChatMemory(Integer userId, Integer sessionId, String memory) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        return redisPoolManager.executeWithRetry(template -> {
            Long removeCount = template.opsForList().remove(key, 0, memory);
            if (removeCount == null || removeCount == 0) {
                return false;
            }
            template.expire(key, DEFAULT_TTL);
            return true;
        });
    }

    /**
     * 批量存储对话历史（使用Pipeline优化）
     * @param userId
     * @param sessionId
     * @param memories 对话历史列表
     */
    public void batchSetChatMemory(Integer userId, Integer sessionId, List<String> memories) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        redisPoolManager.executePipeline(template -> {
            for (String memory : memories) {
                template.opsForList().rightPush(key, memory);
            }
            template.expire(key, DEFAULT_TTL);
        });
    }

    /**
     * 获取最后一次对话（带重试机制）
     * @param userId
     * @param sessionId
     * @return
     */
    public String getLastChatMemory(Integer userId, Integer sessionId) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        return redisPoolManager.executeWithRetry(template -> {
            template.expire(key, DEFAULT_TTL);
            return (String) template.opsForList().getLast(key);
        });
    }

    /**
     * 获取最后一次对话,并删除（带重试机制）
     * @param userId
     * @param sessionId
     * @return
     */
    public String popLastChatMemory(Integer userId, Integer sessionId) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        return redisPoolManager.executeWithRetry(template ->
                (String) template.opsForList().rightPop(key));
    }

    /**
     * 获取对话历史（带重试机制）
     * 增加 limit 参数，防止 Prompt Token 溢出
     * 例如：limit=10 表示只获取最近 10 条
     * @param userId
     * @param sessionId
     * @param limit
     * @return
     */
    public List<Object> getChatMemory(Integer userId, Integer sessionId, Integer limit) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        if (limit == 0) return null;
        return redisPoolManager.executeWithRetry(template -> {
            // 直接使用负数索引：-limit 表示取最后 limit 个元素
            long start = (limit != null && limit > 0) ? -limit : 0;
            template.expire(key, DEFAULT_TTL);
            return template.opsForList().range(key, start, -1);
        });
    }

    public List<?> getAllChatMemory(Integer userId, Integer sessionId) {
        return getChatMemory(userId, sessionId, 20); // 默认给个保护限制，例如 20 条
    }

    public Long getChatMemoryCount(Object userId, Object sessionId) {
        String key = SystemConfig.CHAT_MEMORY.formatted(userId, sessionId);
        return redisPoolManager.executeWithRetry(template -> {
            if (Boolean.FALSE.equals(template.hasKey(key))) return -1L;
            return template.opsForList().size(key);
        });
    }

    /**
     * 存入当前活跃的 Session ID, 如果存在，则更新前活跃的 Session ID的时间
     * @param userId
     * @param sessionId
     */
    public void setOrIncreaseActivateSessionId(Integer userId, Integer sessionId) {
        String key = SystemConfig.USER_ACTIVATE_SESSION.formatted(userId);
        redisPoolManager.executeWithRetry(template -> {
            // 将过期时间以score的形式存入zset
            template.opsForZSet().add(key, sessionId, toDoubleSeconds(LocalDateTime.now().plusHours(1)));
            return null;
        });
    }

    /**
     * 定时删除 USER_ACTIVATE_SESSION中过期的Session ID
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public void deleteActivateSessionId(Integer userId) {
        String zSetKey = SystemConfig.USER_ACTIVATE_SESSION.formatted(userId);
        // 步骤1：获取当前时间的秒数（标准化，避免时区问题）
        double currentTimeSec = toDoubleSeconds(LocalDateTime.now());
        // 步骤2：Redis批量操作（一次executeWithRetry，减少重试开销）
        Set<Object> expiredSessionIds = redisPoolManager.executeWithRetry(template -> {
            // 1. 查过期元素：分数≤当前时间的Session ID
            Set<Object> expired = template.opsForZSet().rangeByScore(zSetKey, 0, currentTimeSec);
            if (expired == null || expired.isEmpty()) {
                return Collections.emptySet();
            }
            // 2. 删除ZSet中的过期元素（批量删除，比循环remove高效）
            template.opsForZSet().removeRangeByScore(zSetKey, 0, currentTimeSec);

            return expired;
        });
        // 步骤3：批量更新MySQL状态（避免循环update，提升性能）
        if (!expiredSessionIds.isEmpty()) {
            List<String> sessionIdList = expiredSessionIds.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            UpdateWrapper<UserChatMemory> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("user_id", userId)
                    .in("session_id", sessionIdList) // 批量in查询，替代循环eq
                    .set("status", 1);
            userChatMemoryMapper.update(updateWrapper);
        }
    }

//    /**
//     * 定时删除 USER_ACTIVATE_SESSION中过期的Session ID
//     */
//    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
//    public void deleteActivateSessionIdBySessionId(Integer userId, Integer sessionId) {
//        String zSetKey = SystemConfig.USER_ACTIVATE_SESSION.formatted(userId);
//        // 步骤1：获取当前时间的秒数（标准化，避免时区问题）
//        double currentTimeSec = toDoubleSeconds(LocalDateTime.now());
//        // 步骤2：Redis批量操作（一次executeWithRetry，减少重试开销）
//        Set<Object> expiredSessionIds = redisPoolManager.executeWithRetry(template -> {
//            // 1. 查过期元素：分数≤当前时间的Session ID
//            Set<Object> expired = template.opsForZSet().range(zSetKey, 0, currentTimeSec);
//            if (expired == null || expired.isEmpty()) {
//                return Collections.emptySet();
//            }
//            // 2. 删除ZSet中的过期元素（批量删除，比循环remove高效）
//            template.opsForZSet().removeRangeByScore(zSetKey, 0, currentTimeSec);
//
//            return expired;
//        });
//        // 步骤3：批量更新MySQL状态（避免循环update，提升性能）
//        if (!expiredSessionIds.isEmpty()) {
//            List<String> sessionIdList = expiredSessionIds.stream()
//                    .map(Object::toString)
//                    .collect(Collectors.toList());
//            UpdateWrapper<UserChatMemory> updateWrapper = new UpdateWrapper<>();
//            updateWrapper.eq("user_id", userId)
//                    .in("session_id", sessionIdList) // 批量in查询，替代循环eq
//                    .set("status", 1);
//            userChatMemoryMapper.update(updateWrapper);
//        }
//    }

    /**
     * 写SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public Boolean setSessionChat(Integer userId, Integer sessionId, String message) {
        String key = SystemConfig.SESSION_SUMMARY.formatted(userId);
        return redisPoolManager.executeWithRetry(template -> template.opsForHash().putIfAbsent(key, sessionId.toString(), message));
    }

    /**
     * 写多个SessionChat（带重试机制）
     * @param userId
     */
    public void setBatchSessionChat(Integer userId, Map<String, String> map) {
        String key = SystemConfig.SESSION_SUMMARY.formatted(userId);
        redisPoolManager.executeWithRetry(template -> {
            template.opsForHash().putAll(key, map);
            return null;
        });
    }

    /**
     * 读SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public String getSessionChat(Integer userId, Integer sessionId) {
        String key = SystemConfig.SESSION_SUMMARY.formatted(userId);
        return (String) redisPoolManager.executeWithRetry(template -> template.opsForHash().get(key, sessionId.toString()));
    }

    /**
     * 读所有的SessionChat（带重试机制）
     * @param userId
     */
    public Map<Object, Object> getAllSessionChat(Integer userId) {
        String key = SystemConfig.SESSION_SUMMARY.formatted(userId);
        return redisPoolManager.executeWithRetry(template -> template.opsForHash().entries(key));
    }

    /**
     * 读SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public Long removeSessionChat(Integer userId, Integer sessionId) {
        String key = SystemConfig.SESSION_SUMMARY.formatted(userId);
        return redisPoolManager.executeWithRetry(template -> template.opsForHash().delete(key, sessionId.toString()));
    }



    public static double toDoubleSeconds(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return Double.NaN;
        }
        ZonedDateTime zonedDateTime = localDateTime.atZone(DEFAULT_ZONE);
        long timestampSec = zonedDateTime.toInstant().getEpochSecond();
        return (double) timestampSec;
    }

    /**
     * 扫描指定前缀的Redis Key（生产级SCAN，非阻塞）
     */
    public Set<String> getAllKeysByScan(String pattern) {
        Set<String> keys = new HashSet<>();
        redisPoolManager.executeWithRetry(template -> {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            Cursor<String> cursor = template.scan(scanOptions);
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            cursor.close();
            return null;
        });
        return keys;
    }
    //    public Set<String> getAllKeysByScan(String pattern) {
//        Set<String> keys = new HashSet<>();
//        AtomicReference<Cursor<String>> cursor = new AtomicReference<>();
//        try {
//            redisPoolManager.executeWithRetry(template -> {
//                ScanOptions scanOptions = ScanOptions.scanOptions()
//                        .match(pattern)
//                        .count(100)
//                        .build();
//                cursor.set(template.scan(scanOptions));
//                while (cursor.get().hasNext()) {
//                    keys.add(cursor.get().next());
//                }
//                cursor.get().close();
//                return null;
//            });
//            return keys;
//        } finally {
//            if (cursor.get() != null) {
//                cursor.get().close();
//            }
//        }
//    }

}
