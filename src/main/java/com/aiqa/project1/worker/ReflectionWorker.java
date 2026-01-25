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
            你是多智能体系统的回答质检节点，核心职责为：校验历史信息 / 检索信息的信息量充足度，判断其是否能支撑回答节点完整、准确地解答用户当前问题。
            <用户问题>
            %s
            </用户问题>
            
            
            <历史信息以及查询信息>
            %s
            </历史信息以及查询信息>
            
            
            判定标准：
            信息是否覆盖用户问题的核心要素，无关键维度缺失；
            信息是否具备有效性，能直接或间接推导问题答案；
            若信息存在缺口，需明确标注缺口类型与补充方向。
            输出要求：仅输出<信息充足> 或者 <信息不足 + 缺口说明>两种结论，无需额外扩展。
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

        String retrievalQuery = state.getRetrievalQuery();

        String query1 = douBaoLite.chat(REFLECTION_TEMPLATE.formatted(
                (retrievalQuery == null || retrievalQuery.isEmpty()) ? state.getQuery(): retrievalQuery,
                chatHistory));
//        // 设置最大子问题数量
//        state.setMaxSubtasksCount(1);

        // 1.检查回答是否合格，或者是否达到最大迭代次数
        if (query1.equals("<信息充足>") || query1.equals("信息充足") || state.getMaxReflection() <= 0) {
            rabbitTemplate.convertAndSend("task.gather.topic", "subtasks", state);

//            rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);
            return;
        }
        // 2. 递减次数，继续循环
        state.setMaxReflection(state.getMaxReflection() - 1);
        redisStoreUtils.setChatMemory(
                state.getUserId(),
                state.getSessionId(),
                """
                <系统改进建议>
                现有查询缺乏以下方面的信息：
                <需求>
                %s
                </需求>
                </系统改进建议>
                """.formatted(query1)
        );
        rabbitTemplate.convertAndSend("refection.direct", "have.problem", state);
    }
}
