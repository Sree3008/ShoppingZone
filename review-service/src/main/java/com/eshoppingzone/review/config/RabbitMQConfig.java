package com.eshoppingzone.review.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public static final String REVIEW_CREATED_ROUTING_KEY = "eshoppingzone.review.created";
    public static final String REVIEW_UPDATED_ROUTING_KEY = "eshoppingzone.review.updated";
    public static final String REVIEW_APPROVED_ROUTING_KEY = "eshoppingzone.review.approved";
    public static final String REVIEW_REJECTED_ROUTING_KEY = "eshoppingzone.review.rejected";
    public static final String REVIEW_DELETED_ROUTING_KEY = "eshoppingzone.review.deleted";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
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
                log.debug("[review-service] Message confirmed by broker. correlationId={}", id);
            } else {
                log.warn("[review-service] Message NACK'd by broker. correlationId={}, cause={}", id, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("[review-service] Message returned (unroutable). exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        return rabbitTemplate;
    }
}
