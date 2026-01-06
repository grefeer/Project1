package com.aiqa.project1.newworker;

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

@Component
public class IntentRouterWorker {
    private final OpenAiChatModel douBaoLite;
    private final String INTENT_ROUTER_TEMPLATE= """
            请模拟多智能体协作系统的「任务复杂度评估官」，负责依据任务内容和历史上下文判定任务的复杂度级别，为智能体任务分配决策提供支撑。
            
            首先，请阅读以下历史上下文：
            <历史上下文>
            %s
            </历史上下文>
            
            接下来，请阅读当前需要评估的任务内容：
            <当前任务内容>
            %s
            </当前任务内容>
            
            你需要依据以下标准判定任务的复杂度级别，输出三个分类结果中的一个：<直接回答任务>、<串行任务>、<并行任务>。分级判定标准如下：
            - <直接回答任务>：任务属于简单事实（非实时热点，指常识性、固定性、无需实时更新的客观事实），且可从历史上下文或任务内容中直接提取答案；
            - <串行任务>：不满足直接回答任务的条件，需要进行信息检索，且当前问题拆分为的子任务存在逻辑依赖关系（必须先完成前一个子任务，才能执行后一个子任务）；
            - <并行任务>：不满足直接回答任务的条件，需要进行信息检索，且当前问题可拆分为多个互相独立、逻辑上无依赖关系的子任务（各子任务可同时并行检索，无需等待其他子任务结果，最终仅需合并子任务结果即可得出答案）。
            
            输出格式：不需要输出其他内容，请直接输出分类结果。
            
            # 输出示例
            <案例一>
            问题：北京今天的天气怎么样？
            分类结果：
            <串行任务>
            </案例一>
            <案例二>
            问题：北京今天的天气怎么样？
            历史信息：北京日间天气晴朗，最高温度7°C，最低温度-2°C
            分类结果：
            <直接回答任务>
            </案例二>
            <案例三>
            问题：北京的日间最高温度比沈阳的高还是低？
            分类结果：
            <并行任务>
            </案例三>
            <案例四>
            问题：论文1的观点和论文2的观点相比，是否更优？
            分类结果：
            <并行任务>
            </案例四>
            <案例五>
            问题：沈阳今天日间气温比上海市的高还是低？
            分类结果：
            <并行任务>
            </案例五>
            <案例六>
            问题：从北京到上海的高铁中途经过哪些城市？先查北京到上海的高铁线路，再列出途经站点
            分类结果：
            <串行任务>
            </案例六>
            现在，请你根据<TASK_CONTENT>和<HISTORY_CONTEXT>进行问题分类。
            """;
    private final RabbitTemplate rabbitTemplate;
    private final CacheAsideUtils cacheAsideUtils;
    private final RedisStoreUtils redisStoreUtils;

    public IntentRouterWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, CacheAsideUtils cacheAsideUtils, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.cacheAsideUtils = cacheAsideUtils;
        this.redisStoreUtils = redisStoreUtils;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "intent.router", durable = "true"),
            exchange = @Exchange(value = "intent.router.direct", type = ExchangeTypes.DIRECT), // 开始交换机，用于判断是需要用网络查询还是数据库查询
            key = "start"
    ))
    public void run(State state) {
        String query = state.getQuery();
        String chatMemory = String.join("\n", cacheAsideUtils.getChatMemory(state.getUserId(), state.getSessionId()));
        String prompt = INTENT_ROUTER_TEMPLATE.formatted(chatMemory, query);

        String chat = douBaoLite.chat(prompt);
        if (chat.contains("直接回答任务") || chat.contains("直接回答")) {
            redisStoreUtils.putSubtaskCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), SystemConfig.CONSTANT_DIRECT_ANSWER_MODE);
            state.setMaxSubtasksCount(SystemConfig.CONSTANT_DIRECT_ANSWER_MODE);
            rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);

        } else if (chat.contains("串行任务") || chat.contains("串行")) {
            state.setMaxSubtasksCount(1);
            state.setParams("task0");
            state.setRetrievalQuery(state.getQuery());
            redisStoreUtils.putSubtaskCount(state.getUserId(), state.getSessionId(), state.getMemoryId(), 1);
            rabbitTemplate.convertAndSend("Start", "start", state);

        } else if (chat.contains("并行任务") || chat.contains("并行")) {
            rabbitTemplate.convertAndSend("task.divide",  state);

        } else {
            rabbitTemplate.convertAndSend("Start", "start", state);
        }
    }
}
