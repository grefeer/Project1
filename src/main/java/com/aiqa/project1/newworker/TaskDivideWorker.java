package com.aiqa.project1.newworker;

import com.aiqa.project1.config.SystemConfig;
import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.CacheAsideUtils;
import com.aiqa.project1.utils.RedisStoreUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TaskDivideWorker {
    private final OpenAiChatModel douBaoLite;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String TASK_DIVIDE_TEMPLATE= """
            你的任务是将一个长查询拆分为多个独立、完整的子查询。每个子查询应能单独理解和执行，且子查询之间互不重叠、无任何依赖关系，可以并行执行。
            
            以下是需要拆分的长查询：
            <long_query>
            %s
            </long_query>
            
            请按照以下规则进行拆分：
            1. 每个子查询必须是一个完整的问题或指令，能够独立存在
            2. 子查询之间应覆盖原长查询的所有信息，且没有重复内容
            3. 子查询之间无任何依赖关系，可以并行执行
            4. 拆分后的子查询数量应适当，避免过于琐碎或过于笼统
            5. 保持原查询的核心意图和关键信息
            
            请将拆分后的子查询以JSON格式返回，键为"tasks"，值为包含所有子查询的数组。例如：
            {"tasks": ["第一个子查询内容", "第二个子查询内容", "第三个子查询内容"]}
            
            现在开始拆分。
            """;
    private final RabbitTemplate rabbitTemplate;
    private final CacheAsideUtils cacheAsideUtils;
    private final RedisStoreUtils redisStoreUtils;

    public TaskDivideWorker(OpenAiChatModel douBaoLite, RabbitTemplate rabbitTemplate, CacheAsideUtils cacheAsideUtils, RedisStoreUtils redisStoreUtils) {
        this.douBaoLite = douBaoLite;
        this.rabbitTemplate = rabbitTemplate;
        this.cacheAsideUtils = cacheAsideUtils;
        this.redisStoreUtils = redisStoreUtils;
    }

    @RabbitListener(queuesToDeclare = @Queue(value = "task.divide", durable = "true"))
    public void run(State state) throws Exception {
        String query = state.getQuery();
        String prompt = TASK_DIVIDE_TEMPLATE.formatted(query);

        String chat = douBaoLite.chat(prompt);
        List<String> stringListMap = this.parseLLMJson(chat);
        redisStoreUtils.putSubtaskCount(
                state.getUserId(),
                state.getSessionId(),
                state.getMemoryId(),
                stringListMap.size());

        for (int i = 0; i < stringListMap.size(); i++) {
            String string = stringListMap.get(i);
            State state1 = new State(
                    state.getUserId(),
                    state.getSessionId(),
                    state.getMemoryId(),
                    state.getQuery());
            state1.setRetrievalQuery(string);
            state1.setParams("task" + i);
            state1.setMaxSubtasksCount(stringListMap.size());
            rabbitTemplate.convertAndSend("Start", "start", state1);
        }
    }

    public List<String> parseLLMJson(String llmOutput) throws Exception {
        // 1. 清理LLM输出中可能的多余字符（比如前后的说明文字、空格）
        String cleanedJson = llmOutput.trim()
                // 移除JSON前后可能的非JSON字符（LLM可能多输出"答："等）
                .replaceAll("^[^\\{]*", "")
                .replaceAll("[^\\}]*$", "");

        // 2. 解析为目标Map（TypeReference解决泛型擦除问题）
        Map<String, List<String>> stringListMap = OBJECT_MAPPER.readValue(cleanedJson, new TypeReference<Map<String, List<String>>>() {});
        List<String> tasks;
        try {
            tasks = stringListMap.get("tasks");
        } catch (Exception e) {
            e.printStackTrace();
            tasks = stringListMap.get(stringListMap.keySet().iterator().next());
        }
        return tasks;
    }
}
