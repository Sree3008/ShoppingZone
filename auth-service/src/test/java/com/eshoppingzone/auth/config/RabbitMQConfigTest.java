package com.eshoppingzone.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void exchangeIsDurable() {
        TopicExchange exchange = config.exchange();
        assertEquals("eshoppingzone.exchange", exchange.getName());
        assertTrue(exchange.isDurable());
    }

    @Test
    void userRegisteredQueueIsDurable() {
        Queue queue = config.userRegisteredQueue();
        assertEquals("eshoppingzone.user.registered.queue", queue.getName());
        assertTrue(queue.isDurable());
    }

    @Test
    void userRegisteredBindingIsCorrect() {
        Queue queue = config.userRegisteredQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.userRegisteredBinding(queue, exchange);

        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.user.registered", binding.getRoutingKey());
    }

    @Test
    void rabbitTemplateHasMandatoryEnabled() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertNotNull(template);
        assertTrue(template.isMandatoryFor(mock(org.springframework.amqp.core.Message.class)));
    }
}
