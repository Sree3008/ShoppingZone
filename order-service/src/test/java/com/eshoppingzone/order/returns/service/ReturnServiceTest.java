package com.eshoppingzone.order.returns.service;

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
import com.eshoppingzone.order.returns.exception.DuplicateReturnException;
import com.eshoppingzone.order.returns.exception.InvalidReturnStateException;
import com.eshoppingzone.order.returns.repository.OrderReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;

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
class ReturnServiceTest {

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

    private Order sampleOrder;
    private OrderItem sampleItem;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setId(10L);
        sampleOrder.setOrderNumber("ORD-1001");
        sampleOrder.setCustomerId(50L);
        sampleOrder.setStatus(OrderStatus.DELIVERED);
        sampleOrder.setDeliveredAt(LocalDateTime.now().minusDays(5));

        sampleItem = new OrderItem();
        sampleItem.setId(100L);
        sampleItem.setOrder(sampleOrder);
        sampleItem.setProductId(500L);
        sampleItem.setProductName("Test Smartphone");
        sampleItem.setUnitPrice(new BigDecimal("299.99"));
        sampleItem.setQuantity(2);
        sampleItem.setLineTotal(new BigDecimal("599.98"));

        sampleOrder.setItems(new ArrayList<>(List.of(sampleItem)));
    }

    // 1. Valid return for delivered order within 30 days
    @Test
    @DisplayName("1. Valid return for delivered order within 30 days")
    void testValidReturnCreation() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(0);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(100L)).thenReturn(Collections.emptyList());
        when(orderReturnRepository.findByReturnNumber(anyString())).thenReturn(Optional.empty());
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> {
            OrderReturn r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Screen flickering");
        ReturnDto result = returnService.createReturn(50L, request, null);

        assertNotNull(result);
        assertEquals(1, result.getQuantity());
        assertEquals(new BigDecimal("299.99"), result.getRefundAmount());
        assertEquals(ReturnStatus.RETURN_REQUESTED, result.getStatus());
        assertTrue(result.getReturnNumber().startsWith("RET-"));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), anyString(), any(ReturnEvent.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    // 2. Missing deliveredAt
    @Test
    @DisplayName("2. Missing deliveredAt throws InvalidReturnStateException")
    void testMissingDeliveredAt() {
        sampleOrder.setDeliveredAt(null);
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertEquals("Delivery timestamp is unavailable for this order", ex.getMessage());
    }

    // 3. Expired 30-day window
    @Test
    @DisplayName("3. Expired 30-day window throws InvalidReturnStateException")
    void testExpired30DayWindow() {
        sampleOrder.setDeliveredAt(LocalDateTime.now().minusDays(31));
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("Return window has expired"));
    }

    // 4. Wrong customer
    @Test
    @DisplayName("4. Wrong customer throws InvalidReturnStateException")
    void testWrongCustomer() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(999L, request, null));
        assertTrue(ex.getMessage().contains("does not belong to the authenticated customer"));
    }

    // 5. Order not DELIVERED
    @Test
    @DisplayName("5. Order not DELIVERED throws InvalidReturnStateException")
    void testOrderNotDelivered() {
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("Returns can only be requested for DELIVERED orders"));
    }

    // 6. Invalid orderItemId
    @Test
    @DisplayName("6. Invalid orderItemId throws InvalidReturnStateException")
    void testInvalidOrderItemId() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 9999L, 1, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("does not belong to order ID"));
    }

    // 7. Quantity <= 0
    @Test
    @DisplayName("7. Quantity <= 0 throws InvalidReturnStateException")
    void testQuantityZeroOrNegative() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 0, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("must be greater than zero"));
    }

    // 8. Quantity exceeds purchased quantity
    @Test
    @DisplayName("8. Quantity exceeds purchased quantity throws InvalidReturnStateException")
    void testQuantityExceedsPurchased() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(0);

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 3, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("exceeds purchased quantity"));
    }

    // 9. Quantity exceeds remaining returnable quantity
    @Test
    @DisplayName("9. Quantity exceeds remaining returnable quantity")
    void testQuantityExceedsRemainingReturnable() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        // Already returned 1 of 2
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(1);

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 2, ReturnReason.DEFECTIVE, "Fails");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("exceeds remaining returnable quantity"));
    }

    // 10. Invalid return reason
    @Test
    @DisplayName("10. ReturnReason OTHER without description throws InvalidReturnStateException")
    void testOtherReasonWithoutDescription() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(0);

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.OTHER, "   ");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, request, null));
        assertTrue(ex.getMessage().contains("Description is required when return reason is OTHER"));
    }

    // 11. Duplicate active return
    @Test
    @DisplayName("11. Duplicate active return throws DuplicateReturnException")
    void testDuplicateActiveReturn() {
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(0);

        OrderReturn activeReturn = new OrderReturn();
        activeReturn.setQuantity(1);
        activeReturn.setReturnReason(ReturnReason.DEFECTIVE);
        activeReturn.setStatus(ReturnStatus.RETURN_REQUESTED);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(100L)).thenReturn(List.of(activeReturn));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Already pending");
        assertThrows(DuplicateReturnException.class, () ->
                returnService.createReturn(50L, request, null));
    }

    // 12. Idempotency behavior
    @Test
    @DisplayName("12. Idempotent return request returns existing return")
    void testIdempotencyBehavior() {
        OrderReturn existing = new OrderReturn();
        existing.setId(123L);
        existing.setReturnNumber("RET-EXISTING");
        existing.setOrderId(10L);
        existing.setCustomerId(50L);
        existing.setStatus(ReturnStatus.RETURN_REQUESTED);
        existing.setQuantity(1);
        existing.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findByIdempotencyKey("IDEMP-KEY-1")).thenReturn(Optional.of(existing));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Screen flickering");
        ReturnDto result = returnService.createReturn(50L, request, "IDEMP-KEY-1");

        assertNotNull(result);
        assertEquals("RET-EXISTING", result.getReturnNumber());
        verify(orderReturnRepository, never()).save(any());
    }

    // 13. Valid state transitions
    @Test
    @DisplayName("13. Valid state transitions through full lifecycle")
    void testValidStateTransitions() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-FLOW");
        ret.setOrderId(10L);
        ret.setCustomerId(50L);
        ret.setProductId(500L);
        ret.setQuantity(1);
        ret.setRefundAmount(new BigDecimal("299.99"));
        ret.setStatus(ReturnStatus.RETURN_REQUESTED);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));

        // RETURN_REQUESTED -> RETURN_APPROVED
        ReturnDto approved = returnService.approveReturn(1L);
        assertEquals(ReturnStatus.RETURN_APPROVED, approved.getStatus());

        // RETURN_APPROVED -> RETURN_PICKUP_PENDING
        ReturnDto pickupPending = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKUP_PENDING));
        assertEquals(ReturnStatus.RETURN_PICKUP_PENDING, pickupPending.getStatus());

        // RETURN_PICKUP_PENDING -> RETURN_PICKED_UP
        ReturnDto pickedUp = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PICKED_UP));
        assertEquals(ReturnStatus.RETURN_PICKED_UP, pickedUp.getStatus());

        // RETURN_PICKED_UP -> RETURN_RECEIVED
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));
        ReturnDto received = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));
        assertEquals(ReturnStatus.RETURN_RECEIVED, received.getStatus());
        assertTrue(received.isInventoryRestocked());

        // RETURN_RECEIVED -> RETURN_PROCESSING
        ReturnDto processing = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_PROCESSING));
        assertEquals(ReturnStatus.RETURN_PROCESSING, processing.getStatus());
    }

    // 14. Invalid state transitions
    @Test
    @DisplayName("14. Invalid state transitions throw InvalidReturnStateException")
    void testInvalidStateTransitions() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setStatus(ReturnStatus.RETURN_REQUESTED);
        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));

        // Cannot jump directly from RETURN_REQUESTED to RETURN_COMPLETED
        assertThrows(InvalidReturnStateException.class, () ->
                returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED)));
    }

    // 15. Inventory restock success
    @Test
    @DisplayName("15. Inventory restock success on RETURN_RECEIVED")
    void testInventoryRestockSuccess() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-RESTOCK-1");
        ret.setProductId(500L);
        ret.setQuantity(1);
        ret.setStatus(ReturnStatus.RETURN_PICKED_UP);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));
        assertTrue(result.isInventoryRestocked());
        assertEquals(RestockStatus.SUCCESS, result.getInventoryRestockStatus());
        assertNull(result.getInventoryRestockFailureReason());
    }

    // 16. Inventory restock failure
    @Test
    @DisplayName("16. Inventory restock failure retains RETURN_RECEIVED but marks FAILED")
    void testInventoryRestockFailure() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-RESTOCK-FAIL");
        ret.setProductId(500L);
        ret.setQuantity(1);
        ret.setStatus(ReturnStatus.RETURN_PICKED_UP);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenThrow(new RuntimeException("Inventory service timeout"));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));
        assertEquals(ReturnStatus.RETURN_RECEIVED, result.getStatus());
        assertFalse(result.isInventoryRestocked());
        assertEquals(RestockStatus.FAILED, result.getInventoryRestockStatus());
        assertEquals("Inventory service timeout", result.getInventoryRestockFailureReason());
    }

    // 17. Inventory restock retry
    @Test
    @DisplayName("17. Inventory restock retry succeeds after failure")
    void testInventoryRestockRetry() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-RETRY");
        ret.setProductId(500L);
        ret.setQuantity(1);
        ret.setStatus(ReturnStatus.RETURN_RECEIVED);
        ret.setInventoryRestocked(false);
        ret.setInventoryRestockStatus(RestockStatus.FAILED);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryClient.restock(any(StockReservationRequest.class))).thenReturn(ApiResponse.success(null));

        ReturnDto result = returnService.retryRestock(1L);
        assertTrue(result.isInventoryRestocked());
        assertEquals(RestockStatus.SUCCESS, result.getInventoryRestockStatus());
    }

    // 18. Duplicate restock prevention
    @Test
    @DisplayName("18. Duplicate restock prevention throws InvalidReturnStateException")
    void testDuplicateRestockPrevention() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-DONE");
        ret.setInventoryRestocked(true);
        ret.setInventoryRestockStatus(RestockStatus.SUCCESS);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));

        assertThrows(InvalidReturnStateException.class, () -> returnService.retryRestock(1L));
        verify(inventoryClient, never()).restock(any());
    }

    // 19. Return completion
    @Test
    @DisplayName("19. Return completion transitions to RETURN_COMPLETED")
    void testReturnCompletionTransition() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-COMPLETED");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_PROCESSING);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));

        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(777L);
        refundResp.setRefundReference("REF-777");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));
        assertEquals(ReturnStatus.RETURN_COMPLETED, result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    // 20. Order changes to RETURNED
    @Test
    @DisplayName("20. Order changes to RETURNED when return is completed")
    void testOrderChangesToReturnedOnCompletion() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-ORDER-RETURNED");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_PROCESSING);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(777L);
        refundResp.setRefundReference("REF-777");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));

        assertEquals(OrderStatus.RETURNED, sampleOrder.getStatus());
        verify(orderRepository).save(sampleOrder);
    }

    // 21. Refund initiation success
    @Test
    @DisplayName("21. Refund initiation success records PENDING refund and refundId")
    void testRefundInitiationSuccess() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-REFUND-OK");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_PROCESSING);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));

        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(888L);
        refundResp.setRefundReference("REF-888");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));

        assertEquals(ReturnStatus.RETURN_COMPLETED, result.getStatus());
        assertEquals(RefundStatus.PENDING, result.getRefundStatus());
        assertEquals(888L, result.getRefundId());
        assertEquals("REF-888", result.getRefundReference());
    }

    // 22. Refund initiation failure
    @Test
    @DisplayName("22. Refund initiation failure records FAILED refund status but keeps RETURN_COMPLETED")
    void testRefundInitiationFailure() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-REFUND-FAIL");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_PROCESSING);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenThrow(new RuntimeException("Payment service unavailable"));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));

        assertEquals(ReturnStatus.RETURN_COMPLETED, result.getStatus());
        assertEquals(RefundStatus.FAILED, result.getRefundStatus());
        assertEquals("Payment service unavailable", result.getRefundFailureReason());
    }

    // 23. Refund retry
    @Test
    @DisplayName("23. Refund retry succeeds after refund failure")
    void testRefundRetrySuccess() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-REFUND-RETRY");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_COMPLETED);
        ret.setRefundStatus(RefundStatus.FAILED);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));

        RefundResponseDto refundResp = new RefundResponseDto();
        refundResp.setId(999L);
        refundResp.setRefundReference("REF-999");
        when(paymentClient.requestRefundInternal(any(RefundRequestDto.class))).thenReturn(ApiResponse.success(refundResp));

        ReturnDto result = returnService.retryRefund(1L);
        assertEquals(RefundStatus.PENDING, result.getRefundStatus());
        assertEquals(999L, result.getRefundId());
        assertEquals("REF-999", result.getRefundReference());
    }

    // 24. Duplicate refund prevention
    @Test
    @DisplayName("24. Duplicate refund prevention throws InvalidReturnStateException")
    void testDuplicateRefundPrevention() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setStatus(ReturnStatus.RETURN_COMPLETED);
        ret.setRefundId(101L);
        ret.setRefundStatus(RefundStatus.PENDING);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));

        assertThrows(InvalidReturnStateException.class, () -> returnService.retryRefund(1L));
        verify(paymentClient, never()).requestRefundInternal(any());
    }

    // 25. Concurrent return quantity protection
    @Test
    @DisplayName("25. Concurrent return quantity protection via pessimistic lock and balance check")
    void testConcurrentReturnQuantityProtection() {
        // Order item quantity = 2
        // Suppose request 1 already consumed 2 (active returns sum = 2)
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(2);

        CreateReturnRequest secondRequest = new CreateReturnRequest(10L, 100L, 2, ReturnReason.DEFECTIVE, "Screen flickering");
        InvalidReturnStateException ex = assertThrows(InvalidReturnStateException.class, () ->
                returnService.createReturn(50L, secondRequest, null));

        assertTrue(ex.getMessage().contains("exceeds remaining returnable quantity (0)"));
    }

    // 26. Database-level idempotency unique constraint catch
    @Test
    @DisplayName("26. Database-level unique idempotency key constraint violation recovers existing return")
    void testDatabaseLevelIdempotencyUniqueConstraintCaught() {
        when(orderReturnRepository.findByIdempotencyKey("RACE-KEY-1")).thenReturn(Optional.empty());
        when(orderRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(100L)).thenReturn(0);
        when(orderReturnRepository.findActiveReturnsByOrderItemId(100L)).thenReturn(Collections.emptyList());
        when(orderReturnRepository.findByReturnNumber(anyString())).thenReturn(Optional.empty());

        // Simulate concurrent insert hitting unique constraint
        when(orderReturnRepository.save(any(OrderReturn.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry for key idx_order_returns_idempotency"));

        OrderReturn existingFromDb = new OrderReturn();
        existingFromDb.setId(99L);
        existingFromDb.setReturnNumber("RET-CONCURRENT-WINNER");
        existingFromDb.setOrderId(10L);
        existingFromDb.setCustomerId(50L);
        existingFromDb.setQuantity(1);
        existingFromDb.setRefundAmount(new BigDecimal("299.99"));
        existingFromDb.setStatus(ReturnStatus.RETURN_REQUESTED);

        // When caught, the service queries findByIdempotencyKey again and finds the winner's return
        when(orderReturnRepository.findByIdempotencyKey("RACE-KEY-1")).thenReturn(Optional.empty(), Optional.of(existingFromDb));

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Screen flickering");
        ReturnDto result = returnService.createReturn(50L, request, "RACE-KEY-1");

        assertNotNull(result);
        assertEquals("RET-CONCURRENT-WINNER", result.getReturnNumber());
    }

    // 27. Duplicate refund protection in performRefund
    @Test
    @DisplayName("27. Duplicate refund protection in performRefund skips calling payment service if refundId exists or pending")
    void testDuplicateRefundSkippedWhenAlreadyInitiated() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-ALREADY-REFUNDED");
        ret.setOrderId(10L);
        ret.setStatus(ReturnStatus.RETURN_PROCESSING);
        ret.setRefundId(555L);
        ret.setRefundStatus(RefundStatus.PENDING);
        ret.setRefundAmount(new BigDecimal("299.99"));

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_COMPLETED));

        assertEquals(ReturnStatus.RETURN_COMPLETED, result.getStatus());
        assertEquals(555L, result.getRefundId());
        // Verify paymentClient was never called again!
        verify(paymentClient, never()).requestRefundInternal(any());
    }

    // 28. Duplicate restock protection in performRestock
    @Test
    @DisplayName("28. Duplicate restock protection in performRestock skips calling inventory service if restock already SUCCESS")
    void testDuplicateRestockSkippedWhenAlreadySuccess() {
        OrderReturn ret = new OrderReturn();
        ret.setId(1L);
        ret.setReturnNumber("RET-ALREADY-RESTOCKED");
        ret.setOrderId(10L);
        ret.setProductId(500L);
        ret.setQuantity(1);
        ret.setStatus(ReturnStatus.RETURN_PICKED_UP);
        ret.setInventoryRestocked(true);
        ret.setInventoryRestockStatus(RestockStatus.SUCCESS);

        when(orderReturnRepository.findById(1L)).thenReturn(Optional.of(ret));
        when(orderReturnRepository.save(any(OrderReturn.class))).thenAnswer(i -> i.getArgument(0));

        ReturnDto result = returnService.updateReturnStatus(1L, new UpdateReturnStatusRequest(ReturnStatus.RETURN_RECEIVED));

        assertEquals(ReturnStatus.RETURN_RECEIVED, result.getStatus());
        // Verify inventoryClient was never called again!
        verify(inventoryClient, never()).restock(any());
    }
}
