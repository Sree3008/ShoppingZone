package com.eshoppingzone.order.concurrency;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.entity.PaymentMethod;
import com.eshoppingzone.order.entity.PaymentStatus;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.repository.OrderSagaStateRepository;
import com.eshoppingzone.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderCheckoutConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:orderconcdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
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
    private ProfileClient profileClient;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderSagaStateRepository sagaStateRepository;

    @BeforeEach
    void setUp() {
        sagaStateRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        Mockito.reset(cartClient, productClient, inventoryClient, paymentClient, profileClient, rabbitTemplate);

        CartItemDto cartItem = new CartItemDto(1L, 10L, "Concurrent Smartphone", new BigDecimal("150.00"), 2, new BigDecimal("300.00"));
        CartDto cartDto = new CartDto(1L, 100L, List.of(cartItem), 2, new BigDecimal("300.00"));
        when(cartClient.getCartByCustomerId(100L)).thenReturn(ApiResponse.success(cartDto));

        ProductSnapshotDto productSnapshot = new ProductSnapshotDto(10L, 2L, "Concurrent Smartphone", new BigDecimal("150.00"), "ACTIVE");
        when(productClient.getProductById(10L)).thenReturn(ApiResponse.success(productSnapshot));

        // Simulate a small 50ms network delay during inventory reservation so concurrent threads overlap
        when(inventoryClient.reserveStock(any(StockReservationRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(50);
            return ApiResponse.success(null);
        });

        PaymentResponseDto paymentRes = new PaymentResponseDto(1L, 1L, 100L, new BigDecimal("300.00"), "WALLET", "SUCCESS", "TXN-CONC-1", null);
        when(paymentClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(ApiResponse.success(paymentRes));
        when(inventoryClient.confirmStock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        when(cartClient.clearCustomerCart(100L)).thenReturn(ApiResponse.success(null));
    }

    @Test
    @DisplayName("Real DB Concurrency: Two simultaneous checkouts with same idempotency key create exactly 1 Order and 1 Saga")
    void testConcurrentCheckout_SameKey_CreatesExactlyOneOrderAndSaga() throws Exception {
        String idempotencyKey = "ORDER-CONC-KEY-999";
        CheckoutRequest request = new CheckoutRequest(null, PaymentMethod.WALLET);

        long orderCountBefore = orderRepository.count();
        long sagaCountBefore = sagaStateRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<OrderDto> checkoutTask = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return orderService.checkout(100L, request, idempotencyKey);
        };

        Future<OrderDto> future1 = executor.submit(checkoutTask);
        Future<OrderDto> future2 = executor.submit(checkoutTask);

        OrderDto result1 = future1.get(10, TimeUnit.SECONDS);
        OrderDto result2 = future2.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        assertNotNull(result1, "Result from thread 1 must not be null");
        assertNotNull(result2, "Result from thread 2 must not be null");

        // 1. Both callers receive the same logical order
        assertEquals(result1.getOrderNumber(), result2.getOrderNumber(), "Both callers must receive the exact same order number");
        assertEquals(result1.getId(), result2.getId(), "Both callers must receive the exact same order ID");
        assertEquals(OrderStatus.CONFIRMED, result1.getStatus(), "Order status must be CONFIRMED");
        assertEquals(OrderStatus.CONFIRMED, result2.getStatus(), "Both responses must reflect the CONFIRMED order");

        // 2. Database verification: exactly 1 Order created
        long orderCountAfter = orderRepository.count();
        assertEquals(orderCountBefore + 1, orderCountAfter, "Order count in database must increase by exactly 1");

        Optional<Order> orderInDb = orderRepository.findByIdempotencyKey(idempotencyKey);
        assertTrue(orderInDb.isPresent(), "Order with the idempotency key must exist in database");
        assertEquals(result1.getOrderNumber(), orderInDb.get().getOrderNumber());

        // 3. Database verification: exactly 1 Saga created
        long sagaCountAfter = sagaStateRepository.count();
        assertEquals(sagaCountBefore + 1, sagaCountAfter, "Saga count in database must increase by exactly 1");

        Optional<OrderSagaState> sagaInDb = sagaStateRepository.findByIdempotencyKey(idempotencyKey);
        assertTrue(sagaInDb.isPresent(), "Saga with the idempotency key must exist in database");
        assertEquals(orderInDb.get().getId(), sagaInDb.get().getOrderId(), "Saga must reference the winning order ID");

        // 4. Downstream side effect verification: exactly once execution
        verify(inventoryClient, times(1)).reserveStock(any(StockReservationRequest.class));
        verify(paymentClient, times(1)).processPayment(any(ProcessPaymentRequest.class));
        verify(inventoryClient, times(1)).confirmStock(any(StockReservationRequest.class));
        verify(cartClient, times(1)).clearCustomerCart(100L);
    }
}
