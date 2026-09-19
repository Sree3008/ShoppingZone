package com.eshoppingzone.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String EXCHANGE_NAME = "eshoppingzone.exchange";
    public static final String USER_REGISTERED_QUEUE = "eshoppingzone.user.registered.queue";
    public static final String USER_REGISTERED_ROUTING_KEY = "eshoppingzone.user.registered";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange exchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(exchange).with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("[auth-service] Message confirmed by broker. correlationId={}", id);
            } else {
                log.warn("[auth-service] Message NACK'd by broker. correlationId={}, cause={}", id, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("[auth-service] Message returned (unroutable). exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        return rabbitTemplate;
    }
}
