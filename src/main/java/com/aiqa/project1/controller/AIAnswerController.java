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
import java.util.List;
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
        Integer ragMode = Integer.valueOf(paramMap.get("ragMode"));
        Integer userId = Integer.parseInt(JwtUtils.getUserIdFromToken(token));


        return questionAnsweringService.queryRegister(userId, sessionId, question, ragMode);
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
        System.out.println("调用 /qa/status");
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));

        Result answerStatus = questionAnsweringService.getAnswerStatus(sessionId, userId, memoryId);
        System.out.println("answerStatus:" + answerStatus);
        return answerStatus;
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
        Result chatMemory = questionAnsweringService.getChatMemory(userId);
        System.out.println("chatMemory:" + chatMemory);
        return chatMemory;
    }

    /**
     * 创建新的session
     * @param token
     * @return json格式相应，code：状态码，message：状态信息，data：一个map，键是sessionId 值是session名称
     */
    @GetMapping("/chatMemory/create")
    public Result createSession(
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        Result session = questionAnsweringService.createSession(userId);
        System.out.println("session:" + session);
        return session;
    }

    /**
     * 获取历史消息
     * @param sessionId
     * @param token
     * @return json格式相应，code：状态码（200回答完成，206返回部分回答数据，但是没回答完，其他失败），message：状态信息，data：一个map，包含以下键值对：sessionId，answer，currentChatMemoryCount，
     *         sessionId是当前会话的Id，answer是历史消息，currentChatMemoryCount是返回的对话数
     */
    @GetMapping("/chatMemory/{sessionId:\\d+}")
    public Result getChatMemoryBySessionId(
            @PathVariable Integer sessionId,
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        Result result = questionAnsweringService.getAnswerStatus(sessionId, userId, 0);
        System.out.println("发送：");
        System.out.println(result);
        return result;
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
     * 输入问题，删除问题以及对应的思考和回答
     * @param sessionId
     * @param memoryIds
     * @param token
     * @return json格式相应，code：状态码（200成功，其他失败），message：状态信息，data：null
     */
    @DeleteMapping("/chatMemory/delete/{sessionId}")
    public Result deleteChatMemoryBySessionId(
            @PathVariable Integer sessionId,
            @RequestBody List<Long> memoryIds,
            @RequestHeader("Authorization") String token) {
        Integer userId = Integer.valueOf(JwtUtils.getUserIdFromToken(token));
        return questionAnsweringService.deleteChatMemory(userId, sessionId, memoryIds);
    }

    /**
     * 这个代码是一个智能文档系统的后端代码以及部分前端代码以及部分接口文档，为这个后端代码编写前端代码，要求：
     * 问答界面主要逻辑如下：一开始会调用“/api/v1/qa/chatMemory”获取所有对话的元信息，包括sessionId以及sessionName；
     * 之后根据这个在边栏处显示历史对话的sessionName；
     * 点击新对话之后，调用/api/v1/qa/chatMemory/create/{sessionId}，创建新对话；
     * 之后用户在问答框发送问题后，把问题传输给/api/v1/qa/ask，之后每2s调用一次 /api/v1/qa/status 流式地返回思考以及回答,
     */

}


