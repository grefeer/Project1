package com.aiqa.project1.utils;

import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class CacheAsideUtils {
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final SessionChatMapper sessionChatMapper;
    private final RabbitTemplate rabbitTemplate;


    public CacheAsideUtils(RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, SessionChatMapper sessionChatMapper, RabbitTemplate rabbitTemplate) {
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.sessionChatMapper = sessionChatMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 写SessionChat（带重试机制）
     * @param userId
     * @param sessionId
     */
    public Boolean setSessionChat(Integer userId, Integer sessionId, String message) {
        try {
            sessionChatMapper.updateSessionNameByUserIdAndSessionId(userId, sessionId, message);
            redisStoreUtils.removeSessionChat(userId);
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
            SessionChat sessionChat = sessionChatMapper.selectOne(queryWrapper);
            if (sessionChat == null) return null;

            result = sessionChat.getSessionName();
            Boolean favorites = sessionChat.getFavorites();
            if (result != null && favorites != null) {
                redisStoreUtils.setSessionChat(userId, sessionId, result + "::" + favorites);
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
    public Map<String, String> getAllSessionChat(Integer userId) {
        try {
            Map<Object, Object> sessionChat = redisStoreUtils.getAllSessionChat(userId);
            // redis命中
            Map<String, String> result = new HashMap<>();
            sessionChat.forEach((o, o2) -> result.put((String) o, (String) o2));

            if (result != null && !result.isEmpty()) {
                return result;
            }

            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            List<SessionChat> sessionChats = sessionChatMapper.selectList(queryWrapper);
            System.out.println(sessionChats);
            Map<String, String> map = sessionChats
                    .stream()
                    .collect(Collectors.toMap(sessionChat1 -> sessionChat1.getSessionId().toString(), sessionChat1 -> sessionChat1.getSessionName() + "::" + sessionChat1.getFavorites().toString()));

            System.out.println(map);
            if (map != null) {
                redisStoreUtils.setBatchSessionChat(userId, map);
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取用户当前的最高 SessionId (Redis 优先)
     * @param userId 用户ID
     * @return 当前最大的 sessionId，如果没有则返回 0
     */
    public Integer getOrSyncMaxSessionId(Integer userId) {
        try {
            // 1. 尝试从 Redis 获取 (假设 redisStoreUtils 已实现 getUserMaxSessionId)
            Integer maxId = redisStoreUtils.getUserMaxSessionId(userId);
            if (maxId != null) {
                return maxId;
            }

            // 2. Redis 缺失，查询数据库
            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId)
                    .select("MAX(session_id) as session_id");
            SessionChat sessionChat = sessionChatMapper.selectOne(queryWrapper);

            int dbMaxId = (sessionChat != null && sessionChat.getSessionId() != null)
                    ? sessionChat.getSessionId() : 0;

            // 3. 回写 Redis 并设置过期时间（例如 24 小时），防止僵尸数据
            redisStoreUtils.setUserMaxSessionId(userId, dbMaxId);
            return dbMaxId;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 更新用户最大会话 ID 并同步数据库
     */
    public void saveNewSession(Integer userId, Integer sessionId, String name, Boolean favorites) {
        // 1. 写入数据库
        if (favorites == null) favorites = false;
        sessionChatMapper.insert(new SessionChat(null, userId, sessionId, name, favorites));

        // 2. 更新 Redis 中的最大 ID 计数器
        redisStoreUtils.setUserMaxSessionId(userId, sessionId);

        // 3. 更新会话名称缓存
        redisStoreUtils.removeSessionChat(userId);
//        redisStoreUtils.setSessionChat(userId, sessionId, name + "::" + favorites);
    }

//    /**
//     * 写ChatMemory（带重试机制）
//     * @param userId
//     * @param sessionId
//     */
//    public Boolean setChatMemory(Integer userId, Integer sessionId, Integer memoryId, String message) {
//        try {
//            userChatMemoryMapper.insertOrUpdate(new UserChatMemory(null, userId, sessionId, memoryId, message, LocalDateTime.now(), 0));
//            redisStoreUtils.removeChatMemory(userId, sessionId, message);
//            return true;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//    }

    /**
     * 异步将redis中的数据同步到mysql中
     * @param state
     * @return
     */
    public Boolean setChatMemory(State state) {
        try {
            // 发送消息到RabbitMQ，异步更新MySQL
            rabbitTemplate.convertAndSend("mysql.update", "chat.memory", state);
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
            result = redisStoreUtils.getChatMemory(userId, sessionId, 10)
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

    /**
     * 读ChatMemory（带重试机制）
     * @param userId
     * @param sessionId
     */
    public List<String> getChatMemory(Integer userId, Integer sessionId, Integer limit) {
        try {
            List<String> result;
            result = redisStoreUtils.getChatMemory(userId, sessionId, limit)
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
            String deletedContent = String.format("<编号%d对话已删除>", memoryId);

            UpdateWrapper<UserChatMemory> queryWrapper = new UpdateWrapper<>();
            queryWrapper
                    .eq("user_id", userId)
                    .eq("session_id", sessionId)
                    .eq("memory_id", memoryId)
                    .set("content", deletedContent);
            int cnt = userChatMemoryMapper.update(queryWrapper);
            return flag && cnt > 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
