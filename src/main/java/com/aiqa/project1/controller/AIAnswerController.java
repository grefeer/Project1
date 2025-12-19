package com.aiqa.project1.controller;


import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.service.impl.QuestionAnsweringService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/qa")
public class AIAnswerController {
    @Autowired
    QuestionAnsweringService questionAnsweringService;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
//    private final Map<String, ScheduledFuture<?>> tasksMap = new ConcurrentHashMap<>();
    @Autowired
    private RedisStoreUtils redisStoreUtils;

//    @Autowired
//    private ThreadPoolTaskScheduler scheduler;

    /**
     * 只把问题发送给大模型，让其回答
     * @param paramMap
     * @param token
     * @return
     */
    @PostMapping("/ask")
    public Result queryRegister(
            @RequestBody Map<String, String> paramMap,
            @RequestHeader("Authorization") String token
    ) {
        String question = paramMap.get("question");
        Integer sessionId = Integer.valueOf(paramMap.get("sessionId"));
        Integer userId = Integer.parseInt(JwtUtils.getUserIdFromToken(token));
        Map<String, Object> data = new HashMap<>();
//        String taskKey = "%s_%s".formatted(userId, sessionId);

        Result resp = new Result();

        try {
            Integer memoryId= Math.toIntExact(redisStoreUtils.getChatMemoryCount(userId, sessionId));
            // -1是用于测试
            questionAnsweringService.answerQuestion(userId, sessionId, memoryId, question);

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
            return Result.define(200,"任务已提交", data);
        } catch (BusinessException e) {
            resp.setCode(e.getCode());
            resp.setMessage(e.getMessage());
            resp.setData(null);
        }
        catch (Exception e) {
            e.printStackTrace();
            resp.setCode(ResponseCode.SERVER_ERROR.getCode());
            resp.setMessage(ResponseCode.SERVER_ERROR.getMessage());
            resp.setData(null);
        }
        return resp;
    }

    /**
     * 流式回答反馈
     * @param sessionId
     * @param token
     * @return
     */
    @GetMapping("/status")
    public Result getStatus(@RequestParam Integer sessionId, @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));

        // 从 Redis 获取当前最新的回答内容
        String currentAnswer = redisStoreUtils.getLastChatMemory(userId, sessionId);

        Map<String, Object> data = new HashMap<>();
        data.put("answer", currentAnswer);
        data.put("sessionId", sessionId);

        if (currentAnswer == null) {
            return Result.define(202, "AI 正在思考中...", data);
        } else if (currentAnswer.contains("<最终回答>")) {
            // 状态：已完成
            return Result.define(200, "回答完成", data);
        } else if (currentAnswer.contains("<AI回答>")){
            // 状态：生成中（中间过程）
            return Result.define(206, "AI 正在输出...", data);
        } else {
            // 状态：生成中
            return Result.define(202, "AI 正在思考中...", data);
        }
    }

}


