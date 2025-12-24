package com.aiqa.project1.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;


@Configuration
public class RabbitConfig {

    // exchange
//    public final static String DEFAULT_EXCHANGE = "project1.fount";

    public final static String DIRECT_EXCHANGE = "project1.direct";

    public final static String TOPIC_EXCHANGE = "project1.topic";

    // queue
    public final static String DEFAULT_QUEUE = "hello.queue1";
    // routing key
    public final static String DEFAULT_ROUTING_KEY = "routing.key.fount";


//    /**
//     * 声明注册 fanout 模式的交换机
//     *
//     * @return 交换机
//     */
//    @Bean
//    public FanoutExchange defalutFanoutExchange() {
//        // durable:是否持久化,默认是false
//        // autoDelete:是否自动删除
//        return new FanoutExchange(DEFAULT_EXCHANGE, true, false);
//    }

    /**
     * 声明注册 direct 模式的交换机
     *
     * @return 交换机
     */
    @Bean
    public DirectExchange defalutDirectExchange() {
        // durable:是否持久化,默认是false
        // autoDelete:是否自动删除
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    /**
     * 声明注册 topic 模式的交换机
     *
     * @return 交换机
     */
    @Bean
    public TopicExchange defalutTopicExchange() {
        // durable:是否持久化,默认是false
        // autoDelete:是否自动删除
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    /**
     * 声明队列
     *
     * @return Queue
     */
    @Bean
    public Queue defaultQueue() {
        // durable:是否持久化,默认true,持久化队列：会被存储在磁盘上，当消息代理重启时仍然存在，暂存队列：当前连接有效
        // exclusive:默认也是false，只能被当前创建的连接使用，而且当连接关闭后队列即被删除。此参考优先级高于durable
        // autoDelete:是否自动删除，当没有生产者或者消费者使用此队列，该队列会自动删除
        return new Queue(DEFAULT_QUEUE, true);
    }

//    /**
//     * 声明绑定交换机与队列
//     *
//     * @return Binding
//     */
//    @Bean
//    public Binding defaultBinding() {
//        return BindingBuilder.bind(defaultQueue()).to(defalutFanoutExchange());
//    }

    /**
     * 声明之后rabbitmq自动将对象转化为json发送，以及将接收的json转换为对象
     * @return
     */
    @Bean
    public MessageConverter defaultMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    @Autowired
    @Qualifier("qaTaskExecutor")
    ThreadPoolTaskExecutor executor;

    @Bean("customContainerFactory")
    public SimpleRabbitListenerContainerFactory containerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                                 ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setTaskExecutor(executor.getThreadPoolExecutor());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(10);
        configurer.configure(factory, connectionFactory);
        return factory;
    }
}
