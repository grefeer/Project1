package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ErrorWorker {

    private final OpenAiChatModel douBaoLite;
    private final RedisStoreUtils redisStoreUtils;
    private final CacheAsideUtils cacheAsideUtils;
    private final RabbitTemplate rabbitTemplate;

    private static final String ERROR_RECOVERY_TEMPLATE = """
            你是系统的安全兜底助手。当前系统在处理用户问题时遇到了局部技术故障。
            你的任务是：利用现有的部分信息（历史对话和可能存在的检索片段），尽量给出一个合理的回答，并礼貌地提示用户系统出现了微小抖动。
            
            <历史对话>
            %s
            </历史对话>
            
            <已获取的参考信息>
            %s
            </已获取的参考信息>
            
            <用户当前问题>
            %s
            </用户当前问题>
            
            请直接给出回答：
            """;

    public ErrorWorker(OpenAiChatModel douBaoLite, RedisStoreUtils redisStoreUtils, 
                       CacheAsideUtils cacheAsideUtils, RabbitTemplate rabbitTemplate) {
        this.douBaoLite = douBaoLite;
        this.redisStoreUtils = redisStoreUtils;
        this.cacheAsideUtils = cacheAsideUtils;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queuesToDeclare = @Queue(value = "error.recovery.queue", durable = "true"))
    public void handleError(State state) {
        log.warn("ErrorWorker 正在介入处理 Session: {}, 错误详情: {}", state.getSessionId(), state.getParams());

        // 1. 获取历史对话
        String chatHistory = String.join("\n", cacheAsideUtils.getChatMemory(state.getUserId(), state.getSessionId()));

        // 2. 从 Redis 获取已经存入的部分检索结果 (对应 AbstractRetrieveWorker 中保存的逻辑)
        List<?> partialContext = redisStoreUtils.getRetrievalInfo(
                state.getUserId(), state.getSessionId(), state.getMemoryId(), "retrieval");
        
        String contextStr = (partialContext == null || partialContext.isEmpty()) ?
                "暂无可用参考资料" :
                partialContext.stream()
                        .map(Object::toString)
                        .distinct()
                        .collect(Collectors.joining("\n"));

        // 3. 构造 Prompt 并调用 LLM
        String prompt = ERROR_RECOVERY_TEMPLATE.formatted(
                chatHistory,
                contextStr,
                state.getQuery()
        );

        try {
            String recoveryAnswer = douBaoLite.chat(prompt);
            
            // 在回答前加上错误标识或直接回复
            String finalAnswer = "[系统提示：部分检索组件响应异常，以下基于现有资料为您回答]\n" + recoveryAnswer;
            
            // 4. 将结果送往结果消费者，结束流程
            state.setParams(finalAnswer); 
            // 假设 ResultConsumer 监听 "result" 队列
            rabbitTemplate.convertAndSend("result", state);
            
        } catch (Exception e) {
            log.error("ErrorWorker 自身也发生异常: ", e);
        }
    }
}