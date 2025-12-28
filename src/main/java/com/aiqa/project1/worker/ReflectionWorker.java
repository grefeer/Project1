package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.RedisStoreUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ReflectionWorker {
    private final OpenAiChatModel douBaoLite;
    private final String REFLECTION_TEMPLATE= """
                你是多智能体系统中的回答质检节点，核心任务是判断历史信息是否能完整回应用户的问题。请严格遵循以下步骤执行质检：
                
                首先，明确质检对象：
                <用户问题>
                %s
                </用户问题>
                
                <历史信息>
                %s
                </历史信息>
                
                接下来，依据以下标准完成判断：
                1. 完整性：历史信息内容是否覆盖了用户问题的所有核心诉求？是否存在关键信息缺失？
                2. 准确性：若问题涉及事实性内容，历史信息内容是否准确无误？
                
                然后，按照以下规则输出结果：
                - 如果历史信息完全满足上述标准，能够完整回答用户问题，请直接返回“无”
                - 如果历史信息存在缺陷（如信息缺失、准确性不足等），请清晰指出具体需要改进的地方，要求：
                a. 明确指出问题类型（如“未回答用户关于XX的疑问”“遗漏了XX关键信息”）
                b. 具体说明需要补充或修正的内容方向
                
                请确保你的判断客观、精准，改进建议具有可操作性。无需额外解释，直接输出结果即可。
                """;
    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;

    public ReflectionWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
    }

    @RabbitListener(queuesToDeclare = @Queue(value = "reflection", durable = "true"))
    public void run(State state) {
        String chatHistory = redisStoreUtils.getChatMemory(
                        state.getUserId(),
                        state.getSessionId(),
                        SystemConfig.MAX_REWRITE_HISTORY_SIZE).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        String query1 = douBaoLite.chat(REFLECTION_TEMPLATE.formatted(state.getQuery(), chatHistory));
        // 1.检查回答是否合格，或者是否达到最大迭代次数
        if (query1.equals("无") || state.getMaxReflection() <= 0) {
            rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);
            return;
        }
        // 2. 递减次数，继续循环
        state.setMaxReflection(state.getMaxReflection() - 1);
        redisStoreUtils.setChatMemory(
                state.getUserId(),
                state.getSessionId(),
                """
                <系统改进建议>
                用户可能会有以下方面的需求，请根据此需求和问题重新回答：
                <需求>
                %s
                </需求>
                </系统改进建议>
                """.formatted(query1)
        );

        // 可能当前的检索的数据库中没有相关数据，换一种检索方法（数据库->网络 or 网络->数据库）
        state.setRetrievalDBFlag(! state.getRetrievalDBFlag());

        rabbitTemplate.convertAndSend("refection.direct", "have.problem", state);
    }
}
