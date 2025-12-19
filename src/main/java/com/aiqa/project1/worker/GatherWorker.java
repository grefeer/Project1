package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.MilvusFilterRetriever;
import com.aiqa.project1.utils.RedisStoreUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class GatherWorker {

    private final RabbitTemplate rabbitTemplate;
    private final RedisStoreUtils redisStoreUtils;


    public GatherWorker(RabbitTemplate rabbitTemplate, RedisStoreUtils redisStoreUtils) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisStoreUtils = redisStoreUtils;
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "gather",durable = "true"),
            exchange = @Exchange(value = "gather.topic", type = ExchangeTypes.TOPIC),
            key = "#.retrieve"
    ))
    public void receiveRetrieveResult(State state) {

        // TODO 储存逻辑,state的检索器数量以及检索信息需要共享，GatherWorker必须是无状态的，如果保存了state，就是有状态的节点了，
        //  所以不能在GatherWorker中保存状态，只能在radis中保存，可以借助langchain4j的永久固化记忆章节

        Long retrievalCount = redisStoreUtils.decreaseRetrievalCount(state.getUserId(), state.getSessionId(), state.getMemoryId());

        if (retrievalCount <= 0) {
            rabbitTemplate.convertAndSend("answer.topic", "have.gathered.retrieve", state);
        } else {
            state.setMaxRetrievalCount(Math.toIntExact(retrievalCount));
        }
    }
}
