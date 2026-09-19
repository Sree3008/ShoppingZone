package com.eshoppingzone.order.returns.flow;

import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.StockReservationRequest;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderItem;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.returns.dto.*;
import com.eshoppingzone.order.returns.entity.OrderReturn;
import com.eshoppingzone.order.returns.enums.RefundStatus;
import com.eshoppingzone.order.returns.enums.RestockStatus;
import com.eshoppingzone.order.returns.enums.ReturnReason;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import com.eshoppingzone.order.returns.repository.OrderReturnRepository;
import com.eshoppingzone.order.returns.service.ReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnEndToEndFlowTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReturnServiceImpl returnService;

    private Order order;
    private OrderItem item;
    private OrderReturn persistedReturn;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(100L);
        order.setOrderNumber("ORD-9999");
        order.setCustomerId(42L);
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now().minusDays(3));

        item = new OrderItem();
        item.setId(200L);
        item.setOrder(order);
        item.setProductId(505L);
        item.setProductName("Wireless Headphones");
        item.setUnitPrice(new BigDecimal("150.00"));
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("300.00"));

        order.setItems(new ArrayList<>(List.of(item)));

        // In-memory simulation of orderReturnRepository.save
        persistedReturn = null;
        lenient().when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(invocation -> {
            OrderReturn r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            persistedReturn = r;
            return r;
        });

        lenient().when(orderReturnRepository.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(persistedReturn));
        lenient().when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Complete Happy Path Return Lifecycle: Customer Request -> Approved -> Pickup -> Received (Restocked) -> Completed (Refunded)")
    void testFullHappyPathReturnLifecycle() {
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(200L)).thenReturn(0);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(200L)).thenReturn(Collections.emptyList());
        when(orderReturnRepository.findByReturnNumber(anyString())).thenReturn(Optional.empty());

        // 1. Customer Create Return
        CreateReturnRequest request = new CreateReturnRequest(100L, 200L, 1, ReturnReason.DAMAGED, "Damaged during transit");
        ReturnDto requested = returnService.createReturn(42L, request, null);

        assertNotNull(requested);
        assertEquals(ReturnStatus.RETURN_REQUESTED, requested.getStatus());
        assertEquals(new BigDecimal("150.00"), requested.getRefundAmount());
        assertNotNull(requested.getRequestedAt());

        // 2. Admin Approve
        ReturnDto approved = returnService.approveReturn(1L);
        assertEquals(ReturnStatus.RETURN_APPROVED, approved.getStatus());
        assertNotNull(approved.getApprovedAt());

        // 3. Pickup Pending
        ReturnDto pickupPending = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKUP_PENDING));
        assertEquals(ReturnStatus.RETURN_PICKUP_PENDING, pickupPending.getStatus());

        // 4. Picked Up
        ReturnDto pickedUp = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKED_UP));
        assertEquals(ReturnStatus.RETURN_PICKED_UP, pickedUp.getStatus());

        // 5. Received (triggers restock)
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        ReturnDto received = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));
        assertEquals(ReturnStatus.RETURN_RECEIVED, received.getStatus());
        assertTrue(received.isInventoryRestocked());
        assertEquals(RestockStatus.SUCCESS, received.getInventoryRestockStatus());
        assertNotNull(received.getReceivedAt());
        verify(inventoryClient).restock(any(StockReservationRequest.class));

        // 6. Processing
        ReturnDto processing = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PROCESSING));
        assertEquals(ReturnStatus.RETURN_PROCESSING, processing.getStatus());
        assertNotNull(processing.getProcessedAt());

        // 7. Completed (Order becomes RETURNED, refund initiated)
        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(555L);
        refundResp.setRefundReference("REF-XYZ-555");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        ReturnDto completed = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));
        assertEquals(ReturnStatus.RETURN_COMPLETED, completed.getStatus());
        assertEquals(OrderStatus.RETURNED, order.getStatus()); // Order set to RETURNED
        assertEquals(RefundStatus.PENDING, completed.getRefundStatus());
        assertEquals(555L, completed.getRefundId());
        assertEquals("REF-XYZ-555", completed.getRefundReference());
        assertNotNull(completed.getCompletedAt());
        verify(paymentClient).requestRefundInternal(any(RefundRequestDto.class));

        // Verify RabbitMQ events emitted across lifecycle
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ReturnEvent> eventCaptor = ArgumentCaptor.forClass(ReturnEvent.class);
        verify(rabbitTemplate, atLeast(7)).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), routingKeyCaptor.capture(), eventCaptor.capture(), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));

        List<ReturnEvent> events = eventCaptor.getAllValues();
        assertEquals("RETURN_REQUESTED", events.get(0).getEventType());
        assertEquals("RETURN_APPROVED", events.get(1).getEventType());
        assertEquals("RETURN_COMPLETED", events.get(events.size() - 1).getEventType());
    }

    @Test
    @DisplayName("Inventory failure during RETURN_RECEIVED -> records FAILED -> retry succeeds")
    void testInventoryFailureAndRetryFlow() {
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(200L)).thenReturn(0);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(200L)).thenReturn(Collections.emptyList());
        when(orderReturnRepository.findByReturnNumber(anyString())).thenReturn(Optional.empty());

        // Create -> Approve -> Pickup Pending -> Picked Up
        returnService.createReturn(42L, new CreateReturnRequest(100L, 200L, 1, ReturnReason.WRONG_ITEM, "Wrong item"), null);
        returnService.approveReturn(1L);
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKUP_PENDING));
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKED_UP));

        // Inventory restock fails
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenThrow(new RuntimeException("Warehouse timeout"));
        ReturnDto receivedFailed = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));

        assertEquals(ReturnStatus.RETURN_RECEIVED, receivedFailed.getStatus());
        assertFalse(receivedFailed.isInventoryRestocked());
        assertEquals(RestockStatus.FAILED, receivedFailed.getInventoryRestockStatus());
        assertEquals("Warehouse timeout", receivedFailed.getInventoryRestockFailureReason());

        // Admin retries restock after inventory service recovery
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        ReturnDto retried = returnService.retryRestock(1L);

        assertTrue(retried.isInventoryRestocked());
        assertEquals(RestockStatus.SUCCESS, retried.getInventoryRestockStatus());
        assertNull(retried.getInventoryRestockFailureReason());
    }

    @Test
    @DisplayName("Refund failure during RETURN_COMPLETED -> records FAILED -> retry succeeds")
    void testRefundFailureAndRetryFlow() {
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(200L)).thenReturn(0);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(200L)).thenReturn(Collections.emptyList());
        when(orderReturnRepository.findByReturnNumber(anyString())).thenReturn(Optional.empty());
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));

        // Progress to RETURN_PROCESSING
        returnService.createReturn(42L, new CreateReturnRequest(100L, 200L, 1, ReturnReason.SIZE_ISSUE, "Size too small"), null);
        returnService.approveReturn(1L);
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKUP_PENDING));
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKED_UP));
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));
        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PROCESSING));

        // Refund fails
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenThrow(new RuntimeException("Payment gateway down"));
        ReturnDto completedFailedRefund = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));

        assertEquals(ReturnStatus.RETURN_COMPLETED, completedFailedRefund.getStatus());
        assertEquals(OrderStatus.RETURNED, order.getStatus());
        assertEquals(RefundStatus.FAILED, completedFailedRefund.getRefundStatus());
        assertEquals("Payment gateway down", completedFailedRefund.getRefundFailureReason());

        // Admin retries refund after payment service recovery
        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(999L);
        refundResp.setRefundReference("REF-999");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        ReturnDto retriedRefund = returnService.retryRefund(1L);
        assertEquals(RefundStatus.PENDING, retriedRefund.getRefundStatus());
        assertEquals(999L, retriedRefund.getRefundId());
        assertEquals("REF-999", retriedRefund.getRefundReference());
        assertNull(retriedRefund.getRefundFailureReason());
    }
}
