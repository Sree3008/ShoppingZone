package com.eshoppingzone.order.audit;

import com.eshoppingzone.order.audit.dto.AuditLogDto;
import com.eshoppingzone.order.audit.entity.AuditLog;
import com.eshoppingzone.order.audit.repository.AuditLogRepository;
import com.eshoppingzone.order.audit.service.AuditLogService;
import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import com.eshoppingzone.order.returns.repository.OrderReturnRepository;
import com.eshoppingzone.order.returns.service.ReturnServiceImpl;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaOrchestrator;
import com.eshoppingzone.order.saga.orchestrator.SagaStateService;
import com.eshoppingzone.order.service.OrderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAuditTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartClient cartClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private ProfileClient profileClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private CheckoutSagaOrchestrator checkoutSagaOrchestrator;

    @Mock
    private SagaStateService sagaStateService;

    @Mock
    private com.eshoppingzone.order.returns.service.ReturnService returnService;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    private ObjectMapper objectMapper;
    private AuditLogService auditLogService;
    private OrderServiceImpl orderService;
    private ReturnServiceImpl returnServiceImpl;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(auditLogRepository, objectMapper, rabbitTemplate);
        orderService = new OrderServiceImpl(
                orderRepository, orderItemRepository, cartClient, productClient, inventoryClient,
                paymentClient, profileClient, rabbitTemplate, checkoutSagaOrchestrator, sagaStateService,
                returnService, auditLogService);
        returnServiceImpl = new ReturnServiceImpl(
                orderReturnRepository, orderRepository, orderItemRepository, inventoryClient,
                paymentClient, rabbitTemplate, auditLogService);
    }

    @Test
    @DisplayName("AuditLog record immutability - updates and deletes are rejected")
    void testAuditLogImmutability() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preUpdate);
        assertThrows(UnsupportedOperationException.class, auditLog::preRemove);
    }

    @Test
    @DisplayName("Checkout completion creates ORDER_CREATED audit event")
    void testCheckoutAudit() {
        Long customerId = 1L;
        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethod(PaymentMethod.WALLET);

        CartDto cart = new CartDto();
        CartItemDto item = new CartItemDto();
        item.setProductId(10L);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        cart.setItems(Collections.singletonList(item));

        when(cartClient.getCartByCustomerId(customerId)).thenReturn(ApiResponse.success(cart));
        ProductSnapshotDto product = new ProductSnapshotDto();
        product.setId(10L);
        product.setName("Test Product");
        product.setPrice(new BigDecimal("100.00"));
        product.setMerchantId(2L);
        when(productClient.getProductById(10L)).thenReturn(ApiResponse.success(product));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        OrderDto orderDto = new OrderDto();
        orderDto.setId(101L);
        orderDto.setOrderNumber("ORD-12345678");
        orderDto.setStatus(OrderStatus.CONFIRMED);
        orderDto.setTotalAmount(new BigDecimal("100.00"));

        when(checkoutSagaOrchestrator.executeSaga(any())).thenReturn(orderDto);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.checkout(customerId, request, "idem-test-1");
        assertNotNull(result);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "ORDER_CREATED".equals(log.getAction()) &&
                "ORDER".equals(log.getResourceType()) &&
                "101".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome()) &&
                "idem-test-1".equals(log.getIdempotencyKey())
        ));
    }

    @Test
    @DisplayName("Admin order status change creates ORDER_STATUS_CHANGED audit event")
    void testUpdateOrderStatusAdminAudit() {
        Long orderId = 101L;
        Order order = new Order();
        order.setId(orderId);
        order.setOrderNumber("ORD-123");
        order.setStatus(OrderStatus.PROCESSING);
        order.setPaymentMethod(PaymentMethod.WALLET);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatusAdmin(orderId, OrderStatus.DELIVERED);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "ORDER_STATUS_CHANGED".equals(log.getAction()) &&
                "101".equals(log.getResourceId()) &&
                "ROLE_ADMIN".equals(log.getActorRole()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Order cancellation creates ORDER_CANCELLED audit event")
    void testCancelOrderAudit() {
        Long orderId = 101L;
        Long customerId = 1L;
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setOrderNumber("ORD-123");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setItems(new ArrayList<>());

        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.cancelOrder(orderId, customerId, "Changed mind");

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "ORDER_CANCELLED".equals(log.getAction()) &&
                "101".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Admin return approval creates RETURN_APPROVED audit event")
    void testApproveReturnAudit() {
        Long returnId = 55L;
        com.eshoppingzone.order.returns.entity.OrderReturn ret = new com.eshoppingzone.order.returns.entity.OrderReturn();
        ret.setId(returnId);
        ret.setReturnNumber("RET-555");
        ret.setOrderId(101L);
        ret.setStatus(ReturnStatus.RETURN_REQUESTED);

        when(orderReturnRepository.findById(returnId)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        returnServiceImpl.approveReturn(returnId);

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "RETURN_APPROVED".equals(log.getAction()) &&
                "RETURN".equals(log.getResourceType()) &&
                "55".equals(log.getResourceId()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }

    @Test
    @DisplayName("Searching audit logs as ADMIN returns paged results")
    void testSearchAuditLogs() {
        AuditLog sample = new AuditLog();
        sample.setId(1L);
        sample.setEventId(UUID.randomUUID().toString());
        sample.setServiceName("order-service");
        sample.setAction("ORDER_CREATED");
        sample.setResourceType("ORDER");
        sample.setOutcome("SUCCESS");

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(sample)));

        Page<AuditLogDto> results = auditLogService.searchAuditLogs(
                null, "ORDER_CREATED", null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertNotNull(results);
        assertEquals(1, results.getTotalElements());
        assertEquals("ORDER_CREATED", results.getContent().get(0).getAction());
    }
}
