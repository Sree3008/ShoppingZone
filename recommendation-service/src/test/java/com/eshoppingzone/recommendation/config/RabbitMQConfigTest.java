package com.eshoppingzone.recommendation.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void declaresProductViewExchangeQueueAndBinding() {
        TopicExchange exchange = config.exchange();
        Queue queue = config.productViewedQueue();
        Binding binding = config.productViewedBinding(queue, exchange);

        assertEquals("eshoppingzone.exchange", exchange.getName());
        assertEquals("eshoppingzone.recommendation.product.viewed.queue", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("eshoppingzone.exchange", binding.getExchange());
        assertEquals("eshoppingzone.recommendation.product.viewed.queue", binding.getDestination());
        assertEquals("eshoppingzone.product.viewed", binding.getRoutingKey());
    }

    @Test
    void declaresDurableDeadLetterQueueAndBinding() {
        Queue queue = config.deadLetterQueue();
        TopicExchange exchange = config.deadLetterExchange();
        Binding binding = config.deadLetterBinding(queue, exchange);

        assertEquals(RabbitMQConfig.DLQ, queue.getName());
        assertEquals(RabbitMQConfig.DLX, exchange.getName());
        assertEquals(RabbitMQConfig.DLX, binding.getExchange());
        assertEquals(RabbitMQConfig.DLQ_ROUTING_KEY, binding.getRoutingKey());
        assertTrue(queue.isDurable());
    }

    @Test
    void createsRabbitListenerFactoryWithRetryDependencies() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                connectionFactory, rabbitTemplate);

        assertNotNull(factory);
    }

    @Test
    void rabbitTemplateHasMandatoryEnabled() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertNotNull(template);
        assertTrue(template.isMandatoryFor(mock(org.springframework.amqp.core.Message.class)));
    }
}
