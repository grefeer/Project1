package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class SummaryWorker {

    private final OpenAiChatModel douBaoLite;
    private static final String SUMMARY_TEMPLATE = """
            你的任务是根据用户的提问，从提供的检索数据中提取所有相关内容。请严格按照以下步骤操作：
            
            首先，明确用户的核心提问：
            <user_question>
            %s
            </user_question>
            
            接下来，查看检索到的数据：
            <retrieved_data>
            %s
            </retrieved_data>
            
            请遵循以下提取规则：
            1. 仅保留与用户提问直接相关的内容，删除无关信息
            2. 确保提取的内容完整，不遗漏关键细节
            3. 保持原文原意，不得篡改或概括（除非原文过于冗长，可适当精简但需保留核心信息）
            4. 如果有多个相关段落，请分别提取并编号
            5. 如果没有相关内容，请明确说明
            
            如果没有相关内容，请输出“检索信息中没有相关内容”。
            
            现在开始执行任务。
                """;
    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;
    private final CacheAsideUtils cacheAsideUtils;

    public SummaryWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils, CacheAsideUtils cacheAsideUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
        this.cacheAsideUtils = cacheAsideUtils;
    }

    @RabbitListener(queuesToDeclare = @Queue("retrievalInformation.summary"))
    public void run(State state) {

        String retrievalInfoText = redisStoreUtils.getRetrievalInfo(state.getUserId(), state.getSessionId(),state.getMemoryId(), "retrieve")
                .stream()
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining("\n"));

        String retrievalQuery = state.getRetrievalQuery();

        System.out.println("AnswerWorker:" + state);

        String prompt = SUMMARY_TEMPLATE.formatted(
                (retrievalQuery == null || retrievalQuery.isEmpty()) ? state.getQuery() : retrievalQuery,
                retrievalInfoText);

        String answer = douBaoLite.chat(prompt);
        Long idx = redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), "<AI思考>" + answer);
        // 延迟，等到redis保存及以后再向rabbitmq发送数据
        if (idx != null && idx > 0) {
            // 简单回答，直接回答，跳过反思
            if (state.getMaxSubtasksCount() == SystemConfig.CONSTANT_DIRECT_ANSWER_MODE) {
                rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);
                return;
            }
            rabbitTemplate.convertAndSend("task.gather.topic", "subtasks", state);
        }
    }
}
