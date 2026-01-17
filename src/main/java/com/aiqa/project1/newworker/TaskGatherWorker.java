package com.aiqa.project1.newworker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.RedisStoreUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskGatherWorker {

    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;


    public TaskGatherWorker(RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "task.gather",durable = "true"),
            exchange = @Exchange(value = "task.gather.topic", type = ExchangeTypes.DIRECT),
            key = "subtasks"
    ))
    public void receiveRetrieveResult(State state) {

        // 储存逻辑,state的检索器数量以及检索信息需要共享，GatherWorker必须是无状态的，如果保存了state，就是有状态的节点了，
        //  所以不能在GatherWorker中保存状态，只能在radis中保存，可以借助langchain4j的永久固化记忆

        Long retrievalCount = redisStoreUtils.decreaseSubtaskCount(state.getUserId(), state.getSessionId(), state.getMemoryId());

        if (retrievalCount <= 0) {
            rabbitTemplate.convertAndSend("refection.direct", "no.problem", state);
//            rabbitTemplate.convertAndSend("reflection", state);
        }
    }
}
