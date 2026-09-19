package com.eshoppingzone.delivery.config;

import org.junit.jupiter.api.Test;
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
    void rabbitTemplateHasMandatoryEnabled() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertNotNull(template);
        assertTrue(template.isMandatoryFor(mock(org.springframework.amqp.core.Message.class)));
    }

    @Test
    void routingKeysAreDefined() {
        assertEquals("delivery.assigned", RabbitMQConfig.ROUTING_KEY_DELIVERY_ASSIGNED);
        assertEquals("delivery.status.changed", RabbitMQConfig.ROUTING_KEY_DELIVERY_STATUS);
    }
}
