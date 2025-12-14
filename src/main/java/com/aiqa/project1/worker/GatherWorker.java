package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import com.aiqa.project1.utils.MilvusFilterRetriever;
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
    private int retrieveNumber = 0;
    private int receievedRetrieveNumber = 0;
    private State state = null;

    public GatherWorker(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(bindings = {
            @QueueBinding(
                    value = @Queue("gather"),
                    exchange = @Exchange(value = "Retrieve", type = ExchangeTypes.DIRECT),
                    key = "gather.retrieve"
            )
    })
    public void receiveRetrieverNumber(Integer retrieveNumber) {
        this.retrieveNumber = retrieveNumber;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("gather"),
            exchange = @Exchange(value = "gather.topic", type = ExchangeTypes.TOPIC),
            key = "*.retrieve"
    ))
    public void receiveRetrieveResult(State state) {
        receievedRetrieveNumber++;
        // TODO 储存逻辑
        if (this.state == null) {
            this.state = state;
        } else {
            this.state.getRetrievalInfo().addAll(state.getRetrievalInfo());
        }
        rabbitTemplate.convertAndSend("answer.topic", "have.gathered.retrieve", state);
    }
}
