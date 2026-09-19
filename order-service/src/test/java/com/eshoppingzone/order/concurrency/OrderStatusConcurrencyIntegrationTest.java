package com.eshoppingzone.order.concurrency;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentMethod;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderStatusConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:orderstatusdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @MockBean
    private CartClient cartClient;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private Long orderId;
    private Long customerId = 55L;
    private Long merchantId = 2L;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        Order order = new Order();
        order.setOrderNumber("ORD-STATUS-100");
        order.setCustomerId(customerId);
        order.setMerchantId(merchantId);
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.WALLET);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        Order saved = orderRepository.save(order);
        orderId = saved.getId();
    }

    @Test
    @DisplayName("Concurrent status updates on Order (cancelOrder vs processMerchantOrder): optimistic locking prevents lost updates")
    void testConcurrentOrderStatusUpdateRace() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 1: Admin processes order (CONFIRMED -> PROCESSING)
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.processMerchantOrder(orderId, merchantId);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException ole) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Customer cancels order (CONFIRMED -> CANCELLED)
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                orderService.cancelOrder(orderId, customerId, "Changed my mind");
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException ole) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent order status update threads timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one status update must succeed");
        assertEquals(1, conflictCount.get(), "The conflicting concurrent update must be rejected by optimistic locking / state validation");

        // Verify database state: order must be either PROCESSING or CANCELLED, never in an invalid or intermediate state
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertTrue(finalOrder.getStatus() == OrderStatus.PROCESSING || finalOrder.getStatus() == OrderStatus.CANCELLED,
                "Final order status must be either PROCESSING or CANCELLED");
    }
}
