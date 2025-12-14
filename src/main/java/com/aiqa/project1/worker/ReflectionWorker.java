package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.Node;
import com.aiqa.project1.nodes.State;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReflectionWorker {
    private final OpenAiChatModel douBaoLite;
    private final String REFLECTION_TEMPLATE= """
                你是多智能体系统中的回答质检节点，核心任务是判断生成的回答是否能完整回应用户的问题。请严格遵循以下步骤执行质检：
                
                首先，明确质检对象：
                <用户问题>
                %s
                </用户问题>
                
                <待质检回答>
                %s
                </待质检回答>
                
                接下来，依据以下标准完成判断：
                1. 完整性：生成回答是否覆盖了用户问题的所有核心诉求？是否存在关键信息缺失？
                2. 相关性：回答内容是否与用户问题直接相关？是否存在答非所问的情况？
                3. 准确性：若问题涉及事实性内容，回答是否准确无误？
                
                然后，按照以下规则输出结果：
                - 如果生成回答完全满足上述标准，能够完整回答用户问题，请直接返回“无”
                - 如果生成回答存在缺陷（如信息缺失、答非所问、准确性不足等），请清晰指出具体需要改进的地方，要求：
                a. 明确指出问题类型（如“未回答用户关于XX的疑问”“遗漏了XX关键信息”）
                b. 具体说明需要补充或修正的内容方向
                
                请确保你的判断客观、精准，改进建议具有可操作性。无需额外解释，直接输出结果即可。
                """;
    private final RabbitTemplate rabbitTemplate;

    public ReflectionWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queuesToDeclare = @Queue("reflection"))
    public void run(State state) {
        String answer = state.getChatMemory().messages().getLast().toString();
        String query1 = douBaoLite.chat(REFLECTION_TEMPLATE.formatted(state.getQuery(), answer));
        // 1.检查回答是否合格，或者是否达到最大迭代次数
        if (query1.equals("无") || state.getMaxReflection() <= 0) {
            rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);
            return;
        }
        // 2. 递减次数，继续循环
        state.setMaxReflection(state.getMaxReflection() - 1);
        state.getChatMemory().add(AiMessage.from("""
                    用户可能会有以下方面的需求，请根据此需求和问题重新回答：
                    
                    <需求>
                    %s
                    </需求>
                    
                    """.formatted(query1)));

        rabbitTemplate.convertAndSend("refection.direct", "have.problem", state);
    }
}
