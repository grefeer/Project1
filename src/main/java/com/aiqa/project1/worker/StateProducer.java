package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.RedisStoreUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StateProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;

    public StateProducer(RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
    }
    // TODO 还是有数据库数据是英文，Query是中文，检索准确率低，迭代次数多 的问题
    public void run(Integer userId, Integer sessionId, Integer memoryId, String query) {
        State state = new State(
                userId,
                sessionId,
                memoryId,
                "<用户问题>" + query
        );

        Long idx = redisStoreUtils.setChatMemory(userId, sessionId, "<用户问题>" + query);

        // 存入当前活跃的 Session ID, 如果存在，则更新前活跃的 Session ID的时间
        redisStoreUtils.setOrIncreaseActivateSessionId(userId, sessionId);

        if (idx != null && idx > 0) {
            rabbitTemplate.convertAndSend("intent.router.direct", "start", state);
        }
    }
}
