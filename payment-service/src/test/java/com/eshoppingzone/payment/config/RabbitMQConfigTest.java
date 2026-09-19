package com.eshoppingzone.payment.config;

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
        assertEquals("eshoppingzone.payment.success", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY);
        assertEquals("eshoppingzone.payment.failed", RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY);
        assertEquals("eshoppingzone.refund.completed", RabbitMQConfig.REFUND_COMPLETED_ROUTING_KEY);
    }
}
