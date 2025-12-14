package com.aiqa.project1.worker;

import com.aiqa.project1.nodes.State;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StateProducer {

    private final RabbitTemplate rabbitTemplate;

    public StateProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void run(State state) {
        rabbitTemplate.convertAndSend("refection.direct", "new.problem", state);
    }
}
