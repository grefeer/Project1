package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import dev.langchain4j.data.message.AiMessage;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResultConsumer {
    private final RabbitTemplate rabbitTemplate;

    public ResultConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("result"),
            exchange = @Exchange(name = "refection.direct", type = ExchangeTypes.DIRECT),
            key = "no.problem"
    ))
    public void receive(State state) {
        System.out.println(state);
    }
}
