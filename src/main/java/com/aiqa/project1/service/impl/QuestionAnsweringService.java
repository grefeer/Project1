package com.aiqa.project1.service.impl;


import com.aiqa.project1.mapper.SessionChatMapper;
import com.aiqa.project1.mapper.UserChatMemoryMapper;
import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.pojo.qa.SessionChat;
import com.aiqa.project1.pojo.qa.SessionChatDTO;
import com.aiqa.project1.pojo.qa.UserChatMemory;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.aiqa.project1.worker.StateProducer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class QuestionAnsweringService {
    private final StateProducer stateProducer;
    private final RedisStoreUtils redisStoreUtils;
    private final UserChatMemoryMapper userChatMemoryMapper;
    private final CacheAsideUtils cacheAsideUtils;
    private final OpenAiChatModel douBaoLite;

    private static final String SESSION_SUMMARY_TEMPLATE = """
    你的任务是根据提供的对话内容，总结生成一个精准、简洁且具有概括性的标题。
    首先，请阅读以下对话内容：
    <对话内容>
    {{对话内容}}
    </对话内容>
    在生成标题时，请遵循以下要求：
    1. 标题需准确概括对话的核心主题或主要事件，避免遗漏关键信息
    2. 语言简洁明了，避免使用冗长复杂的表述
    3. 标题长度控制在10-20字之间，最多不超过25字
    4. 标题应具有一定吸引力，能够体现对话的核心价值或冲突点
    5. 避免使用专业术语或对话中未提及的信息
    请在<标题>标签内输出你总结的标题。
    """;
    private final SessionChatMapper sessionChatMapper;

    public QuestionAnsweringService(StateProducer stateProducer, RedisStoreUtils redisStoreUtils, UserChatMemoryMapper userChatMemoryMapper, CacheAsideUtils cacheAsideUtils, OpenAiChatModel douBaoLite, SessionChatMapper sessionChatMapper) {
        this.stateProducer = stateProducer;
        this.redisStoreUtils = redisStoreUtils;
        this.userChatMemoryMapper = userChatMemoryMapper;
        this.cacheAsideUtils = cacheAsideUtils;
        this.douBaoLite = douBaoLite;
        this.sessionChatMapper = sessionChatMapper;
    }

    /**
     * 将问题交给多智能体回答
     * @param userId
     * @param sessionId
     * @param memoryId
     * @param query
     */
    public void answerQuestion(Integer userId, Integer sessionId, Integer memoryId, String query) {
        stateProducer.run(userId, sessionId, memoryId, query);
    }

    /**
     * 根据sessionId，userId以及memoryId获取思考过程以及回答
     * @param sessionId
     * @param userId
     * @param memoryId
     * @return
     */
    @NotNull
    public Result getAnswerStatus(Integer sessionId, Integer userId, Integer memoryId) {
        // 判断ai是否更新了思考过程或者回答
        Integer currentChatMemoryCount = Math.toIntExact(cacheAsideUtils.getChatMemoryCount(userId, sessionId));
        if (currentChatMemoryCount < memoryId.longValue()) {
            return Result.define(202, "AI 正在思考中...", null);
        } else {
            int count = currentChatMemoryCount - memoryId;
            if (count <= 0) return Result.define(202, "AI 正在思考中...", null); // 至少获取当前最新的一条

            System.out.println(currentChatMemoryCount + ":" + memoryId);
            // 从 Redis 获取当前最新的回答内容
            List<String> currentAnswer = redisStoreUtils.getChatMemory(
                    userId,
                    sessionId,
                    count)
                    .stream()
                    .map(Object::toString)
                    .toList();

            Map<String, Object> data = new HashMap<>();
            data.put("answer", currentAnswer);
            data.put("sessionId", sessionId);
            data.put("currentChatMemoryCount", currentChatMemoryCount);
            if (currentAnswer == null) {
                return Result.define(202, "AI 正在思考中...", data);
            } else if (currentAnswer.getLast().contains("<最终回答>")) {
                // 状态：已完成
                return Result.define(200, "回答完毕", data);
            } else {
                return Result.define(206, "正在回答", data);
            }
        }
    }

    @NotNull
    public Result queryRegister(Integer userId, Integer sessionId, String question, Map<String, Object> data) {
        try {
            Integer memoryId= Math.toIntExact(cacheAsideUtils.getChatMemoryCount(userId, sessionId));
            answerQuestion(userId, sessionId, memoryId, question);
//            if (! tasksMap.containsKey(taskKey)) {
//                // 2. 定义数据检查任务逻辑
//                Runnable dataCheckTask = () -> {
//                    List<?> chatMemoryList = redisStoreUtils.getAllChatMemory(userId, sessionId);
//                    if (chatMemoryList == null || chatMemoryList.isEmpty()) return;
//
//                    // 根据redis更新mysql
//                    userChatMemoryMapper.insertOrUpdate(
//                            chatMemoryList.stream()
//                                    .map(o -> new UserChatMemory(userId, sessionId, memoryId, (String) o))
//                                    .toList());
//                };
//
//                ScheduledFuture<?> dataCheckFuture = scheduler.scheduleAtFixedRate(
//                        dataCheckTask,
//                        Instant.now(),  // 初始延迟（毫秒）
//                        Duration.ofSeconds(30)   // 执行周期（毫秒）
//                );
//                tasksMap.put(taskKey, dataCheckFuture);
//            }
            data.put("userId", userId);
            data.put("sessionId", sessionId);
            data.put("memoryId", memoryId);
            return Result.define(200, "任务已提交", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从MySQL中读取sessionId会话并返回给前端
     * @param userId
     * @return
     */
    @NotNull
    public Result getChatMemoryBySessionId(Integer userId, Integer sessionId) {
        try {
            // 获取user的所有session的chat memory
            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId).eq("session_id", sessionId);
            List<UserChatMemory> userChatMemoryList = userChatMemoryMapper.selectList(queryWrapper);

            // 过滤注销以及异常对话
            userChatMemoryList = userChatMemoryList.stream().filter(UserChatMemory::checkStatus).toList();

            userChatMemoryList.forEach(userChatMemory -> {
                // 如果对话状态为已过期，从MySQL中加载对话
                if ( userChatMemory.getStatus() == 1) {
                    redisStoreUtils.setChatMemory(userId, userChatMemory.getSessionId(), userChatMemory.getContent());
                    userChatMemory.setStatus(0);
                    userChatMemoryMapper.updateById(userChatMemory);
                }
                // 判断未过期的Status是否真的未过期
                if ( userChatMemory.getStatus() == 0) {
                    LocalDateTime userChatMemoryLastActiveTime = userChatMemory.getLastActiveTime();
                    // 过期
                    if (LocalDateTime.now().isAfter(userChatMemoryLastActiveTime.plusHours(1))) {
                        redisStoreUtils.setChatMemory(userId, userChatMemory.getSessionId(), userChatMemory.getContent());
                    }
                }
            });
            return Result.define(200, "任务已提交", userChatMemoryList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从Redis或者MySQL中读取session的整体内容并返回
     * @param userId
     * @return
     */
    @NotNull
    public Result getChatMemory(Integer userId) {
        try {
            Map<String, String> userChatMemoryList = cacheAsideUtils.getAllSessionChat(userId);
            return Result.define(200, "任务已提交", userChatMemoryList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从Redis或者MySQL中创建session的整体内容
     * @param userId
     * @return
     */
    @NotNull
    public Result createSession(Integer userId) {
        try {
            QueryWrapper<SessionChat> queryWrapper = new QueryWrapper<>();
            QueryWrapper<UserChatMemory> queryWrapper_ = new QueryWrapper<>();

            queryWrapper.eq("user_id", userId)
                    .select("max(session_id) as session_id");
            SessionChat sessionChat = sessionChatMapper.selectOne(queryWrapper);

            queryWrapper_.eq("session_id", sessionChat.getSessionId());
            Integer maxSessionId = (sessionChat != null) ? sessionChat.getSessionId() : 1;

            if(userChatMemoryMapper.selectOne(queryWrapper_) != null)
                maxSessionId += 1;

            String result = cacheAsideUtils.getSessionChat(userId, maxSessionId);
            if (result != null) {
                return Result.define(200, "会话已存在", Map.of("sessionName", result, "sessionId", maxSessionId));
            } else {
                cacheAsideUtils.setSessionChat(userId, maxSessionId, "新会话" + maxSessionId);
                return Result.define(200, "任务已提交", Map.of("sessionName", "新会话" + maxSessionId, "sessionId", maxSessionId));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }


    /**
     * 给session重命名
     * @param userId
     * @return
     */
    @NotNull
    public Result reNameChatMemoryBySessionId(Integer userId, Integer sessionId) {
        try {
            String message = "";

            String prompt = SESSION_SUMMARY_TEMPLATE.formatted();
            message = douBaoLite.chat(prompt);
            cacheAsideUtils.setSessionChat(userId, sessionId, message);
            return Result.define(200, "任务已提交", message);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    /**
     * 从MySQL中读取历史所有会话并返回给前端
     * @param userId
     * @return
     */
    @NotNull
    public Result getAllChatMemory(Integer userId) {
        try {
            // 获取user的所有session的chat memory
            QueryWrapper<UserChatMemory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            Map<Integer, SessionChatDTO> tempUserChatMemoryMap = new HashMap<>();
            List<UserChatMemory> userChatMemoryList = userChatMemoryMapper.selectList(queryWrapper);

            userChatMemoryList.forEach(userChatMemory -> {
                // 如果对话状态为已过期，从MySQL中加载对话
                if ( userChatMemory.getStatus() == 1) {
                    redisStoreUtils.setChatMemory(userId, userChatMemory.getSessionId(), userChatMemory.getContent());
                    userChatMemory.setStatus(0);
                    userChatMemoryMapper.updateById(userChatMemory);
                }

                // 按sessionId分组，不存在则创建SessionChatDTO
                SessionChatDTO sessionChatDTO = tempUserChatMemoryMap.computeIfAbsent(
                        userChatMemory.getSessionId(),
                        k -> {
                            SessionChatDTO dto = new SessionChatDTO();
                            dto.setSessionId(userChatMemory.getSessionId());
                            dto.setSessionName(userChatMemory.getSessionId().toString());

                            return dto;
                        }
                );
                // 单个session内已按memoryId升序，直接添加,无需额外排序
                sessionChatDTO.getMessages().add(userChatMemory);
            });
            // 按sessionId升序排序
            Map<Integer, SessionChatDTO> sortedUserChatMemoryMap = new LinkedHashMap<>();
            // 提取所有sessionId并升序排序
            tempUserChatMemoryMap.keySet().stream()
                    .sorted(Integer::compareTo) // sessionId升序
                    .forEach(sessionId -> sortedUserChatMemoryMap.put(sessionId, tempUserChatMemoryMap.get(sessionId)));
            tempUserChatMemoryMap.forEach((integer, sessionChatDTO) -> sessionChatDTO.setLastActiveTime(sessionChatDTO.getMessages().getLast().getLastActiveTime()));
            return Result.define(200, "任务已提交", sortedUserChatMemoryMap);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }

    public Result deleteChatMemory(Integer userId, Integer sessionId, Integer memoryId) {
        try {
            if (cacheAsideUtils.deleteChatMemory(userId, sessionId, memoryId)) {
                return Result.define(200, "删除成功", null);
            }
            return Result.define(404, "删除失败，无该memoryId", null);
        } catch (Exception e){
            e.printStackTrace();
            return Result.define(ResponseCode.SERVER_ERROR.getCode(), ResponseCode.SERVER_ERROR.getMessage(), e);
        }
    }


}
