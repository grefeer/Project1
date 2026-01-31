package com.aiqa.project1.controller;

import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.utils.JwtUtils;
import com.aiqa.project1.utils.SseEmitterManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE控制器，提供前端连接入口
 */
@RestController
@RequestMapping("/api/v1/sse")
@Slf4j
public class SseController {

    @Autowired
    private SseEmitterManager sseEmitterManager;

    /**
     * 建立SSE连接，监听指定文档的状态更新
     * @return SseEmitter 长连接对象
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(HttpServletRequest request, @RequestParam(required = false) String token) {
        String userId;
        // 优先从URL参数解析token（适配SSE）
        if (StringUtils.hasText(token)) {
            userId = JwtUtils.getUserIdFromToken(token);
        } else {
            AuthInfo authInfo = (AuthInfo)request.getAttribute("authInfo");
            userId = authInfo.getUserId();
        }
        log.info("SSE连接建立 - userId: {}", userId);

        SseEmitter emitter;
        try {
            emitter = sseEmitterManager.createSseConnect(userId);
            // 首次连接，推送"连接成功"的初始化消息
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("已建立文档状态监听，用户ID：" + userId)
                    .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            sseEmitterManager.removeConnect(userId);
            throw new RuntimeException("SSE连接初始化失败", e);
        }
        return emitter;
    }


}