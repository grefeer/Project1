package com.aiqa.project1.controller;


import com.aiqa.project1.pojo.ResponseCode;
import com.aiqa.project1.pojo.Result;
import com.aiqa.project1.service.impl.QuestionAnsweringService;
import com.aiqa.project1.utils.BusinessException;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/qa")
public class AIAnswerController {

    private final QuestionAnsweringService questionAnsweringService;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final RedisStoreUtils redisStoreUtils;

    public AIAnswerController(QuestionAnsweringService questionAnsweringService, RedisStoreUtils redisStoreUtils) {
        this.questionAnsweringService = questionAnsweringService;
        this.redisStoreUtils = redisStoreUtils;
    }


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

        return questionAnsweringService.queryRegister(userId, sessionId, question, data);
    }


    /**
     * 流式回答反馈，每次返回部分思考或者回答
     * @param sessionId
     * @param token
     * @return json格式相应，code：状态码（200返回部分回答数据，回答完成，206返回部分回答数据，但是没回答完，其他失败），message：状态信息，data：一个map，包含以下键值对：sessionId，answer，currentChatMemoryCount，
     *         sessionId是当前会话的Id，answer是回答，currentChatMemoryCount是返回的对话数
     */
    @GetMapping("/status")
    public Result getStatus(
            @RequestParam Integer sessionId,
            @RequestParam Integer memoryId,
            @RequestHeader("Authorization") String token
    ) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));

        return questionAnsweringService.getAnswerStatus(sessionId, userId, memoryId);
    }

    /**
     * 获取该用户的所有session的元数据
     * @param token
     * @return json格式相应，code：状态码，message：状态信息，data：一个map，键是sessionId 值是session名称
     */
    @GetMapping("/chatMemory")
    public Result getChatMemory(
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        return questionAnsweringService.getChatMemory(userId);
    }

    /**
     * 获取历史消息
     * @param sessionId
     * @param token
     * @return json格式相应，code：状态码（200回答完成，206返回部分回答数据，但是没回答完，其他失败），message：状态信息，data：一个map，包含以下键值对：sessionId，answer，currentChatMemoryCount，
     *         sessionId是当前会话的Id，answer是历史消息，currentChatMemoryCount是返回的对话数
     */
    @GetMapping("/chatMemory/{sessionId}")
    public Result getChatMemoryBySessionId(
            @PathVariable Integer sessionId,
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        return questionAnsweringService.getAnswerStatus(sessionId, userId, 0);
    }

    /**
     * 前端发现session的对话数到达一定个数后，发送给后端，让后端给这个session起名字
     * @param sessionId
     * @param token
     * @return json格式相应，code：状态码（200成功，其他失败），message：状态信息，data：该session的名字，以的格式发送字符串
     */
    @GetMapping("/chatMemory/reName/{sessionId}")
    public Result reNameChatMemoryBySessionId(
            @PathVariable Integer sessionId,
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        return questionAnsweringService.reNameChatMemoryBySessionId(userId, sessionId);
    }

    /**
     * 删除指定session的指定memory
     * @param sessionId
     * @param memoryId
     * @param token
     * @return json格式相应，code：状态码（200成功，其他失败），message：状态信息，data：null
     */
    @PostMapping("/chatMemory/delete/{sessionId}/{memoryId}")
    public Result deleteChatMemoryBySessionId(
            @PathVariable Integer sessionId,
            @PathVariable Integer memoryId,
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        return questionAnsweringService.deleteChatMemory(userId, sessionId, memoryId);
    }

}


