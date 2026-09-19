package com.eshoppingzone.profile.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

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

    @Test
    void profileQueueIsDurable() {
        Queue queue = config.profileUserRegisteredQueue();
        assertEquals(RabbitMQConfig.PROFILE_USER_REGISTERED_QUEUE, queue.getName());
        assertTrue(queue.isDurable());
    }

    @Test
    void profileUserRegisteredBindingIsCorrect() {
        Queue queue = config.profileUserRegisteredQueue();
        TopicExchange exchange = config.exchange();
        Binding binding = config.profileUserRegisteredBinding(queue, exchange);

        assertEquals(RabbitMQConfig.EXCHANGE_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.USER_REGISTERED_ROUTING_KEY, binding.getRoutingKey());
        assertEquals(RabbitMQConfig.PROFILE_USER_REGISTERED_QUEUE, binding.getDestination());
    }

    @Test
    void exchangeIsDurable() {
        TopicExchange exchange = config.exchange();
        assertTrue(exchange.isDurable());
    }
}
