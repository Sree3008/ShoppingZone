package com.eshoppingzone.order.returns.service;

import com.eshoppingzone.order.client.InventoryClient;
import com.eshoppingzone.order.client.PaymentClient;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.dto.StockReservationItem;
import com.eshoppingzone.order.dto.StockReservationRequest;
import com.eshoppingzone.order.entity.Order;
import com.eshoppingzone.order.entity.OrderItem;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.exception.ResourceNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReturnServiceImpl implements ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnServiceImpl.class);

    private final OrderReturnRepository orderReturnRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final RabbitTemplate rabbitTemplate;

    private com.eshoppingzone.order.audit.service.AuditLogService auditLogService;

    public ReturnServiceImpl(OrderReturnRepository orderReturnRepository,
                             OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             InventoryClient inventoryClient,
                             PaymentClient paymentClient,
                             RabbitTemplate rabbitTemplate) {
        this(orderReturnRepository, orderRepository, orderItemRepository, inventoryClient, paymentClient, rabbitTemplate, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReturnServiceImpl(OrderReturnRepository orderReturnRepository,
                             OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             InventoryClient inventoryClient,
                             PaymentClient paymentClient,
                             RabbitTemplate rabbitTemplate,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.order.audit.service.AuditLogService auditLogService) {
        this.orderReturnRepository = orderReturnRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.rabbitTemplate = rabbitTemplate;
        this.auditLogService = auditLogService;
    }

    public void setAuditLogService(com.eshoppingzone.order.audit.service.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private void auditLog(String action, String resourceType, String resourceId, String outcome,
                          String failureReason, java.util.Map<String, Object> metadata,
                          String actorUserId, String actorUsername, String actorRole, String idempotencyKey) {
        if (auditLogService != null) {
            try {
                auditLogService.log(action, resourceType, resourceId, outcome, failureReason, metadata,
                        actorUserId, actorUsername, actorRole, idempotencyKey);
            } catch (Exception ignored) {
            }
        }
    }

    private java.util.Map<String, Object> safeMeta(Object... keyValues) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object val = (i + 1 < keyValues.length) ? keyValues[i + 1] : null;
            if (val != null) {
                map.put(key, val);
            }
        }
        return map;
    }

    @Override
    @Transactional
    public ReturnDto createReturn(Long customerId, CreateReturnRequest request, String idempotencyKey) {
        if (customerId == null) {
            throw new InvalidReturnStateException("Customer must be authenticated");
        }

        // 11a. Idempotency Key check
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<OrderReturn> existing = orderReturnRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                OrderReturn ret = existing.get();
                if (!ret.getCustomerId().equals(customerId) || !ret.getOrderId().equals(request.getOrderId())) {
                    throw new DuplicateReturnException("Idempotency key already used with different parameters");
                }
                log.info("Idempotent return request matched existing return: {}", ret.getReturnNumber());
                return ReturnDto.fromEntity(ret);
            }
        }

        // 2 & 9. Acquire lock on Order to ensure concurrent return requests cannot exceed returnable quantity
        Order order = orderRepository.findByIdWithLock(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        // 3. Order belongs to authenticated customer
        if (!order.getCustomerId().equals(customerId)) {
            throw new InvalidReturnStateException("Order does not belong to the authenticated customer");
        }

        // 4. Order status is DELIVERED
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new InvalidReturnStateException("Returns can only be requested for DELIVERED orders. Current status: " + order.getStatus());
        }

        // 5. deliveredAt exists
        if (order.getDeliveredAt() == null) {
            throw new InvalidReturnStateException("Delivery timestamp is unavailable for this order");
        }

        // 6. Current date is within 30 days from deliveredAt (deliveredAt + 30 days >= current time)
        if (order.getDeliveredAt().plusDays(30).isBefore(LocalDateTime.now())) {
            throw new InvalidReturnStateException("Return window has expired for this order (30 days from delivery)");
        }

        // 7. orderItemId belongs to the specified order
        OrderItem orderItem = order.getItems().stream()
                .filter(i -> i.getId().equals(request.getOrderItemId()))
                .findFirst()
                .orElseThrow(() -> new InvalidReturnStateException("Order item ID " + request.getOrderItemId() + " does not belong to order ID " + order.getId()));

        // 8. Requested quantity > 0
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidReturnStateException("Return quantity must be greater than zero");
        }

        // 8 & 9. Quantity validation & concurrency safety
        int alreadyReturned = orderReturnRepository.sumNonRejectedCancelledQuantityByOrderItemId(orderItem.getId());
        int remainingReturnable = orderItem.getQuantity() - alreadyReturned;

        if (request.getQuantity() > orderItem.getQuantity()) {
            throw new InvalidReturnStateException("Requested quantity (" + request.getQuantity() + ") exceeds purchased quantity (" + orderItem.getQuantity() + ")");
        }

        if (request.getQuantity() > remainingReturnable) {
            throw new InvalidReturnStateException("Requested return quantity (" + request.getQuantity() + ") exceeds remaining returnable quantity (" + remainingReturnable + ")");
        }

        // 10. Reason validation
        if (request.getReason() == null) {
            throw new InvalidReturnStateException("Return reason is required");
        }
        if (request.getReason() == ReturnReason.OTHER) {
            if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
                throw new InvalidReturnStateException("Description is required when return reason is OTHER");
            }
        }

        // 11b. Business-level duplicate active return check
        List<OrderReturn> activeReturns = orderReturnRepository.findActiveReturnsByOrderItemId(orderItem.getId());
        boolean hasDuplicateActive = activeReturns.stream().anyMatch(r ->
                r.getQuantity().equals(request.getQuantity()) && r.getReturnReason() == request.getReason()
        );
        if (hasDuplicateActive) {
            throw new DuplicateReturnException("An active return request already exists for this order item and quantity");
        }

        // 12. Calculate refund amount (unitPrice * requestedQuantity) using BigDecimal
        BigDecimal refundAmount = orderItem.getUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        // Generate unique return number: RET-XXXXXXXX
        String returnNumber = generateUniqueReturnNumber();

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setReturnNumber(returnNumber);
        orderReturn.setOrderId(order.getId());
        orderReturn.setOrderNumber(order.getOrderNumber());
        orderReturn.setCustomerId(customerId);
        orderReturn.setOrderItemId(orderItem.getId());
        orderReturn.setProductId(orderItem.getProductId());
        orderReturn.setProductName(orderItem.getProductName());
        orderReturn.setQuantity(request.getQuantity());
        orderReturn.setRefundAmount(refundAmount);
        orderReturn.setReturnReason(request.getReason());
        orderReturn.setReturnDescription(request.getDescription());
        orderReturn.setStatus(ReturnStatus.RETURN_REQUESTED);
        orderReturn.setRefundStatus(RefundStatus.NOT_INITIATED);
        orderReturn.setInventoryRestocked(false);
        orderReturn.setInventoryRestockStatus(RestockStatus.NOT_APPLICABLE);
        orderReturn.setIdempotencyKey(idempotencyKey);
        orderReturn.setRequestedAt(LocalDateTime.now());

        OrderReturn saved;
        try {
            saved = orderReturnRepository.save(orderReturn);
        } catch (DataIntegrityViolationException dive) {
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                Optional<OrderReturn> existing = orderReturnRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    log.info("Concurrent insert caught by unique idempotency key constraint. Returning existing return: {}", existing.get().getReturnNumber());
                    return ReturnDto.fromEntity(existing.get());
                }
            }
            throw new DuplicateReturnException("A duplicate return request was detected at database level: " + dive.getMessage());
        }

        log.info("Created return request {} for order item {}", returnNumber, orderItem.getId());

        publishReturnEvent(saved, "RETURN_REQUESTED");
        auditLog("RETURN_REQUESTED", "RETURN", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta(
                        "returnNumber", saved.getReturnNumber(),
                        "orderId", saved.getOrderId(),
                        "customerId", saved.getCustomerId(),
                        "status", saved.getStatus() != null ? saved.getStatus().name() : null
                ),
                String.valueOf(customerId), null, "ROLE_CUSTOMER", idempotencyKey);
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnDto> getCustomerReturns(Long customerId) {
        return orderReturnRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(ReturnDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnDto getCustomerReturnById(Long returnId, Long customerId) {
        OrderReturn ret = orderReturnRepository.findByIdAndCustomerId(returnId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));
        return ReturnDto.fromEntity(ret);
    }

    @Override
    @Transactional
    public ReturnDto cancelReturn(Long returnId, Long customerId) {
        OrderReturn ret = orderReturnRepository.findByIdAndCustomerId(returnId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        validateTransition(ret.getStatus(), ReturnStatus.RETURN_CANCELLED);

        ret.setStatus(ReturnStatus.RETURN_CANCELLED);
        OrderReturn saved = orderReturnRepository.save(ret);
        log.info("Customer {} cancelled return {}", customerId, ret.getReturnNumber());

        auditLog("RETURN_CANCELLED", "RETURN", String.valueOf(returnId), "SUCCESS", null,
                safeMeta(
                        "returnNumber", ret.getReturnNumber(),
                        "orderId", ret.getOrderId(),
                        "customerId", customerId,
                        "status", ReturnStatus.RETURN_CANCELLED.name()
                ),
                String.valueOf(customerId), null, "ROLE_CUSTOMER", null);

        publishReturnEvent(saved, "RETURN_CANCELLED");
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnDto> getAllReturns(ReturnStatus status) {
        if (status != null) {
            return orderReturnRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                    .map(ReturnDto::fromEntity)
                    .collect(Collectors.toList());
        }
        return orderReturnRepository.findAll().stream()
                .map(ReturnDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnDto> getPendingReturns() {
        return orderReturnRepository.findByStatusOrderByCreatedAtDesc(ReturnStatus.RETURN_REQUESTED).stream()
                .map(ReturnDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnDto getReturnByIdAdmin(Long returnId) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));
        return ReturnDto.fromEntity(ret);
    }

    @Override
    @Transactional
    public ReturnDto approveReturn(Long returnId) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        validateTransition(ret.getStatus(), ReturnStatus.RETURN_APPROVED);

        ret.setStatus(ReturnStatus.RETURN_APPROVED);
        ret.setApprovedAt(LocalDateTime.now());
        OrderReturn saved = orderReturnRepository.save(ret);
        log.info("Admin approved return {}", ret.getReturnNumber());

        auditLog("RETURN_APPROVED", "RETURN", String.valueOf(returnId), "SUCCESS", null,
                safeMeta(
                        "returnNumber", ret.getReturnNumber(),
                        "orderId", ret.getOrderId(),
                        "status", ReturnStatus.RETURN_APPROVED.name()
                ),
                null, null, "ROLE_ADMIN", null);

        publishReturnEvent(saved, "RETURN_APPROVED");
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReturnDto rejectReturn(Long returnId, RejectReturnRequest request) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        if (request == null || request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
            throw new InvalidReturnStateException("Rejection reason is required");
        }

        validateTransition(ret.getStatus(), ReturnStatus.RETURN_REJECTED);

        ret.setStatus(ReturnStatus.RETURN_REJECTED);
        ret.setRejectedAt(LocalDateTime.now());
        ret.setRejectionReason(request.getRejectionReason());
        OrderReturn saved = orderReturnRepository.save(ret);
        log.info("Admin rejected return {} with reason: {}", ret.getReturnNumber(), request.getRejectionReason());

        auditLog("RETURN_REJECTED", "RETURN", String.valueOf(returnId), "SUCCESS", null,
                safeMeta(
                        "returnNumber", ret.getReturnNumber(),
                        "orderId", ret.getOrderId(),
                        "reason", request.getRejectionReason(),
                        "status", ReturnStatus.RETURN_REJECTED.name()
                ),
                null, null, "ROLE_ADMIN", null);

        publishReturnEvent(saved, "RETURN_REJECTED");
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReturnDto updateReturnStatus(Long returnId, UpdateReturnStatusRequest request) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        ReturnStatus target = request.getStatus();
        validateTransition(ret.getStatus(), target);

        ret.setStatus(target);

        // Transition side effects
        if (target == ReturnStatus.RETURN_RECEIVED) {
            ret.setReceivedAt(LocalDateTime.now());
            performRestock(ret);
        } else if (target == ReturnStatus.RETURN_PROCESSING) {
            ret.setProcessedAt(LocalDateTime.now());
        } else if (target == ReturnStatus.RETURN_COMPLETED) {
            ret.setCompletedAt(LocalDateTime.now());
            // Section 15: First ensure Order is set to RETURNED and saved
            Order order = orderRepository.findById(ret.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + ret.getOrderId()));
            order.setStatus(OrderStatus.RETURNED);
            orderRepository.save(order);

            // Trigger payment refund
            performRefund(ret);
        }

        OrderReturn saved = orderReturnRepository.save(ret);
        log.info("Updated return {} status to {}", ret.getReturnNumber(), target);

        auditLog("RETURN_STATUS_CHANGED", "RETURN", String.valueOf(returnId), "SUCCESS", null,
                safeMeta(
                        "returnNumber", ret.getReturnNumber(),
                        "orderId", ret.getOrderId(),
                        "status", target != null ? target.name() : null
                ),
                null, null, "ROLE_ADMIN", null);

        publishReturnEvent(saved, target.name());
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReturnDto retryRestock(Long returnId) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        if (ret.isInventoryRestocked() || ret.getInventoryRestockStatus() == RestockStatus.SUCCESS) {
            throw new InvalidReturnStateException("Inventory has already been restocked for return: " + ret.getReturnNumber());
        }

        performRestock(ret);
        OrderReturn saved = orderReturnRepository.save(ret);
        return ReturnDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public ReturnDto retryRefund(Long returnId) {
        OrderReturn ret = orderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));

        if (ret.getStatus() != ReturnStatus.RETURN_COMPLETED) {
            throw new InvalidReturnStateException("Refund can only be retried for COMPLETED returns");
        }

        if (ret.getRefundStatus() == RefundStatus.SUCCESS || ret.getRefundStatus() == RefundStatus.PENDING || ret.getRefundId() != null) {
            throw new InvalidReturnStateException("Refund has already been initiated or processed for return: " + ret.getReturnNumber());
        }

        // Ensure order is RETURNED
        Order order = orderRepository.findById(ret.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + ret.getOrderId()));
        if (order.getStatus() != OrderStatus.RETURNED) {
            order.setStatus(OrderStatus.RETURNED);
            orderRepository.save(order);
        }

        performRefund(ret);
        OrderReturn saved = orderReturnRepository.save(ret);
        return ReturnDto.fromEntity(saved);
    }

    private void performRestock(OrderReturn ret) {
        if (ret.isInventoryRestocked() || ret.getInventoryRestockStatus() == RestockStatus.SUCCESS) {
            log.info("Inventory already restocked for return {}. Skipping duplicate restock call.", ret.getReturnNumber());
            return;
        }

        try {
            StockReservationItem item = new StockReservationItem(ret.getProductId(), ret.getQuantity());
            StockReservationRequest req = new StockReservationRequest(ret.getReturnNumber(), List.of(item));
            ApiResponse<Void> response = inventoryClient.restock(req);

            if (response != null && response.isSuccess()) {
                ret.setInventoryRestocked(true);
                ret.setInventoryRestockStatus(RestockStatus.SUCCESS);
                ret.setInventoryRestockFailureReason(null);
                log.info("Restock successful for return {}", ret.getReturnNumber());
            } else {
                ret.setInventoryRestocked(false);
                ret.setInventoryRestockStatus(RestockStatus.FAILED);
                ret.setInventoryRestockFailureReason(response != null ? response.getMessage() : "Unknown restock error");
                log.warn("Restock failed for return {}: {}", ret.getReturnNumber(), ret.getInventoryRestockFailureReason());
            }
        } catch (Exception e) {
            ret.setInventoryRestocked(false);
            ret.setInventoryRestockStatus(RestockStatus.FAILED);
            ret.setInventoryRestockFailureReason(e.getMessage());
            log.warn("Exception during inventory restock for return {}: {}", ret.getReturnNumber(), e.getMessage());
        }
    }

    private void performRefund(OrderReturn ret) {
        if (ret.getRefundId() != null || ret.getRefundStatus() == RefundStatus.PENDING || ret.getRefundStatus() == RefundStatus.SUCCESS) {
            log.info("Refund already initiated or completed for return {}. Refund ID: {}, Status: {}. Skipping duplicate refund call.",
                    ret.getReturnNumber(), ret.getRefundId(), ret.getRefundStatus());
            return;
        }

        try {
            RefundRequestDto req = new RefundRequestDto(
                    ret.getOrderId(),
                    ret.getRefundAmount(),
                    "Return #" + ret.getReturnNumber() + ": " + ret.getReturnReason()
            );
            ApiResponse<RefundResponseDto> resp = paymentClient.requestRefundInternal(req);

            if (resp != null && resp.getData() != null) {
                ret.setRefundId(resp.getData().getId());
                ret.setRefundReference(resp.getData().getRefundReference());
                ret.setRefundStatus(RefundStatus.PENDING);
                ret.setRefundFailureReason(null);
                log.info("Refund initiated successfully for return {}. Refund ID: {}", ret.getReturnNumber(), resp.getData().getId());
            } else {
                ret.setRefundStatus(RefundStatus.FAILED);
                ret.setRefundFailureReason(resp != null ? resp.getMessage() : "Empty response from payment service");
                log.warn("Refund initiation failed for return {}: {}", ret.getReturnNumber(), ret.getRefundFailureReason());
            }
        } catch (Exception e) {
            ret.setRefundStatus(RefundStatus.FAILED);
            ret.setRefundFailureReason(e.getMessage());
            log.warn("Exception during refund initiation for return {}: {}", ret.getReturnNumber(), e.getMessage());
        }
    }

    private void validateTransition(ReturnStatus current, ReturnStatus target) {
        if (current == null || target == null) {
            throw new InvalidReturnStateException("Current and target status must not be null");
        }

        boolean valid = switch (current) {
            case RETURN_REQUESTED -> (target == ReturnStatus.RETURN_APPROVED ||
                                      target == ReturnStatus.RETURN_REJECTED ||
                                      target == ReturnStatus.RETURN_CANCELLED);
            case RETURN_APPROVED -> (target == ReturnStatus.RETURN_PICKUP_PENDING);
            case RETURN_PICKUP_PENDING -> (target == ReturnStatus.RETURN_PICKED_UP);
            case RETURN_PICKED_UP -> (target == ReturnStatus.RETURN_RECEIVED);
            case RETURN_RECEIVED -> (target == ReturnStatus.RETURN_PROCESSING);
            case RETURN_PROCESSING -> (target == ReturnStatus.RETURN_COMPLETED);
            default -> false;
        };

        if (!valid) {
            throw new InvalidReturnStateException("Invalid state transition from " + current + " to " + target);
        }
    }

    private String generateUniqueReturnNumber() {
        String returnNumber;
        do {
            returnNumber = "RET-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        } while (orderReturnRepository.findByReturnNumber(returnNumber).isPresent());
        return returnNumber;
    }

    private void publishReturnEvent(OrderReturn orderReturn, String eventType) {
        try {
            ReturnEvent event = new ReturnEvent(
                    UUID.randomUUID().toString(),
                    eventType,
                    orderReturn.getId(),
                    orderReturn.getReturnNumber(),
                    orderReturn.getOrderId(),
                    orderReturn.getCustomerId(),
                    orderReturn.getStatus().name(),
                    orderReturn.getRefundAmount(),
                    LocalDateTime.now()
            );

            String routingKey = "eshoppingzone.order.return." + eventType.toLowerCase().replace("_", ".");
            CorrelationData correlationData = new CorrelationData("return-" + orderReturn.getId() + "-" + eventType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event, correlationData);
            log.info("Published return event {} for return {}", eventType, orderReturn.getReturnNumber());
        } catch (Exception e) {
            log.error("Failed to publish RabbitMQ return event {} for return {}: {}", eventType, orderReturn.getReturnNumber(), e.getMessage());
            // Do not rethrow; DB transaction must not rollback solely because of RabbitMQ failure
        }
    }
}
