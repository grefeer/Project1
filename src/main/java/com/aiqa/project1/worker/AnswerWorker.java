package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Component
public class AnswerWorker {

    private final OpenAiChatModel douBaoLite;
    private static final String ANSWER_TEMPLATE = """
                你是多智能体系统的问答节点，核心任务是结合历史对话上下文与检索信息，准确全面回答用户当前问题。
                
                首先，阅读以下历史对话，理解背景及用户此前交互：
                <历史对话>
                %s
                </历史对话>
                
                接下来，查看回答当前问题的检索信息：
                <检索信息>
                %s
                </检索信息>
                
                当前用户问题：
                <用户查询>
                %s
                </用户查询>
                
                回答规则：
                1. 结合历史上下文逻辑，确保回答与之前交互连贯一致，回答简洁明了，保证信息完整准确
                2. 回答内容需基于检索信息以及历史信息，禁止编造未提及内容，若无直接相关信息，明确告知无法回答
                3. 问题和回答的语言要一致，这是十分重要的
                4. 如果用户要求“介绍论文”，回答必须包含论文的核心问题、提出的方法、关键创新点和主要实验结果
                现在，请开始回答。
                """;
    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;
    private final CacheAsideUtils cacheAsideUtils;

    public AnswerWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils, CacheAsideUtils cacheAsideUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
        this.cacheAsideUtils = cacheAsideUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "answerWorker.queue",durable = "true"),
            exchange = @Exchange(name = SystemConfig.REFLECTION_DIRECT, type = ExchangeTypes.DIRECT),
            key = SystemConfig.NO_PROBLEM_KEY
    ))
    public void run(State state) {
        String chatHistory = String.join("\n", cacheAsideUtils.getChatMemory(state.getUserId(), state.getSessionId()));

        String retrievalInfoText = redisStoreUtils.getRetrievalInfo(state.getUserId(), state.getSessionId(),state.getMemoryId(), "retrieve")
                .stream()
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining("\n"));

        String retrievalQuery = state.getRetrievalQuery();

        System.out.println("AnswerWorker:" + state);

        String prompt = ANSWER_TEMPLATE.formatted(
                chatHistory,
                retrievalInfoText,
                (retrievalQuery == null || retrievalQuery.isEmpty()) ? state.getQuery() : retrievalQuery);

        String answer = douBaoLite.chat(prompt);
        Long idx = redisStoreUtils.setChatMemory(state.getUserId(), state.getSessionId(), "<AI思考>" + answer);
        // 延迟，等到redis保存及以后再向rabbitmq发送数据
        if (idx != null && idx > 0) {
            rabbitTemplate.convertAndSend("result", state);
        }
    }
}
