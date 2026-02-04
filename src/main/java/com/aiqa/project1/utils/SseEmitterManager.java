package com.aiqa.project1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE连接管理工具类，维护客户端连接并提供状态推送能力
 */
@Component
public class SseEmitterManager {
    // 存储客户端连接：key-用户/文档标识（可根据业务调整），value-SSE连接对象
    private final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建SSE连接
     * @param clientId 客户端唯一标识（如文档ID/用户ID，根据业务定）
     * @return SseEmitter 连接对象
     */
    public SseEmitter createSseConnect(String clientId) {
        // 设置超时时间：15分钟（可根据业务调整），超时后自动关闭连接
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(15));
        // 存储连接
        sseEmitterMap.put(clientId, emitter);

        // 连接关闭/超时/异常时，移除无效连接
        emitter.onCompletion(() -> sseEmitterMap.remove(clientId));
        emitter.onTimeout(() -> {
            sseEmitterMap.remove(clientId);
            try {
                emitter.complete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        emitter.onError((e) -> {
            sseEmitterMap.remove(clientId);
            try {
                emitter.complete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        return emitter;
    }

    public SseEmitter getSseEmitter(String clientId) {
        return sseEmitterMap.get(clientId);
    }

    /**
     * 推送文档状态给前端
     * @param clientId 客户端/文档唯一标识
     * @param documentStatus 要推送的文档状态（更新后的状态）
     */
    public void sendDocumentStatus(String clientId, Map<String, String> documentStatus) {
        try {
            SseEmitter emitter = sseEmitterMap.get(clientId);
            // 发送SSE事件，指定事件类型为"documentStatus"，方便前端区分
            emitter.send(SseEmitter.event()
                    .name("documentStatus")
                    .data(objectMapper.writeValueAsString(documentStatus))
                    .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            // 推送失败，移除无效连接并记录日志
            sseEmitterMap.remove(clientId);
            System.err.println("推送文档状态失败，客户端ID：" + clientId + "，异常：" + e.getMessage());
        }
//        }
    }

    /**
     * 移除指定连接
     * @param clientId 客户端/文档ID
     */
    public void removeConnect(String clientId) {
        sseEmitterMap.remove(clientId);
    }
}