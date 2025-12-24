package com.aiqa.project1.utils;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class CacheAsideUtils {
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final SessionChatMapper sessionChatMapper;


    public CacheAsideUtils(RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, SessionChatMapper sessionChatMapper) {
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.sessionChatMapper = sessionChatMapper;
    }

    /**
     * 写SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public Boolean setSessionChat(Integer userId, Integer sessionId, String message) {
        try {
            sessionChatMapper.insertOrUpdate(new SessionChat(null, userId, sessionId, message));
            redisStoreUtils.removeSessionChat(userId, sessionId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 读SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public String getSessionChat(Integer userId, Integer sessionId) {
        try {
            String result;
            result = redisStoreUtils.getSessionChat(userId, sessionId);
            // redis命中
            if (result != null) {
                return result;
            }
            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            queryWrapper.eq("session_id", sessionId);
            result = sessionChatMapper.selectOne(queryWrapper).getSessionName();
            if (result != null) {
                redisStoreUtils.setSessionChat(userId, sessionId, result);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读SessionChat（带重试机制）
     * @param userId
     */
    public Map<Integer, String> getAllSessionChat(Integer userId) {
        try {
            Map<Object, Object> sessionChat = redisStoreUtils.getAllSessionChat(userId);
            // redis命中
            Map<Integer, String> result = new HashMap<>();
            sessionChat.forEach((o, o2) -> result.put((Integer) o, (String) o2));
            if (result != null) {
                return result;
            }

            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            Map<Integer, String> map = sessionChatMapper.selectList(queryWrapper)
                    .stream()
                    .collect(Collectors.toMap(SessionChat::getSessionId, SessionChat::getSessionName));
            if (map != null) {
                redisStoreUtils.setBatchSessionChat(userId, map);
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 写ChatMemory（带重试机制）
     * @param userId
     * @param sessionId
     */
    public Boolean setChatMemory(Integer userId, Integer sessionId, Integer memoryId, String message) {
        try {
            userChatMemoryMapper.insertOrUpdate(new UserChatMemory(null, userId, sessionId, memoryId, message, LocalDateTime.now(), 0));
            redisStoreUtils.removeChatMemory(userId, sessionId, message);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 读ChatMemory（带重试机制）
     * @param userId
     * @param sessionId
     */
    public List<String> getChatMemory(Integer userId, Integer sessionId) {
        try {
            List<String> result;
            result = redisStoreUtils.getChatMemory(userId, sessionId, 10000)
                    .stream()
                    .map(Objects::toString)
                    .toList();
            // redis命中
            if (result != null) {
                return result;
            }

            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            queryWrapper.eq("session_id", sessionId);
            result = userChatMemoryMapper.selectList(queryWrapper)
                    .stream()
                    .map(UserChatMemory::getContent)
                    .toList();
            if (result != null) {
                redisStoreUtils.batchSetChatMemory(userId, sessionId, result);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Long getChatMemoryCount(Integer userId, Integer sessionId) {
        Long currentChatMemoryCount = redisStoreUtils.getChatMemoryCount(userId, sessionId);
        // redis不存在，
        if (currentChatMemoryCount == -1L) {
            // 重新从mysql中读取
            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            queryWrapper.eq("session_id", sessionId);
            List<String> result = userChatMemoryMapper.selectList(queryWrapper)
                    .stream()
                    .map(UserChatMemory::getContent)
                    .toList();
            // mysql中存在，redis中没有
            if (result != null) {
                redisStoreUtils.batchSetChatMemory(userId, sessionId, result);
                currentChatMemoryCount = (long) result.size();
            } else {
                // 该session在mysql中没有
                currentChatMemoryCount = 0L;
            }
        }
        return currentChatMemoryCount;
    }

    public Boolean deleteChatMemory(Integer userId, Integer sessionId, Integer memoryId) {
        try {
            Boolean flag = redisStoreUtils.deleteChatMemory(userId, sessionId, memoryId);
            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId).eq("session_id", sessionId).eq("memory_id", memoryId);
            int cnt = userChatMemoryMapper.delete(queryWrapper);
            return flag && cnt > 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
