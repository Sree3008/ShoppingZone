package com.eshoppingzone.order.service;

import com.eshoppingzone.order.client.*;
import com.eshoppingzone.order.config.RabbitMQConfig;
import com.eshoppingzone.order.dto.*;
import com.eshoppingzone.order.entity.*;
import com.eshoppingzone.order.exception.IdempotencyConflictException;
import com.eshoppingzone.order.exception.InsufficientStockException;
import com.eshoppingzone.order.exception.InvalidOrderStateException;
import com.eshoppingzone.order.exception.PaymentException;
import com.eshoppingzone.order.exception.ResourceNotFoundException;
import com.eshoppingzone.order.repository.OrderItemRepository;
import com.eshoppingzone.order.repository.OrderRepository;
import com.eshoppingzone.order.saga.entity.OrderSagaState;
import com.eshoppingzone.order.saga.entity.SagaStatus;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaContext;
import com.eshoppingzone.order.saga.orchestrator.CheckoutSagaOrchestrator;
import com.eshoppingzone.order.saga.orchestrator.SagaStateService;
import com.eshoppingzone.order.saga.util.RequestFingerprintUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartClient cartClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ProfileClient profileClient;
    private final RabbitTemplate rabbitTemplate;
    private final CheckoutSagaOrchestrator checkoutSagaOrchestrator;
    private final SagaStateService sagaStateService;
    private final com.eshoppingzone.order.returns.service.ReturnService returnService;
    private com.eshoppingzone.order.audit.service.AuditLogService auditLogService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CartClient cartClient,
                            ProductClient productClient,
                            InventoryClient inventoryClient,
                            PaymentClient paymentClient,
                            ProfileClient profileClient,
                            RabbitTemplate rabbitTemplate,
                            CheckoutSagaOrchestrator checkoutSagaOrchestrator,
                            SagaStateService sagaStateService,
                            com.eshoppingzone.order.returns.service.ReturnService returnService) {
        this(orderRepository, orderItemRepository, cartClient, productClient, inventoryClient, paymentClient, profileClient, rabbitTemplate, checkoutSagaOrchestrator, sagaStateService, returnService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CartClient cartClient,
                            ProductClient productClient,
                            InventoryClient inventoryClient,
                            PaymentClient paymentClient,
                            ProfileClient profileClient,
                            RabbitTemplate rabbitTemplate,
                            CheckoutSagaOrchestrator checkoutSagaOrchestrator,
                            SagaStateService sagaStateService,
                            com.eshoppingzone.order.returns.service.ReturnService returnService,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.order.audit.service.AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartClient = cartClient;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.profileClient = profileClient;
        this.rabbitTemplate = rabbitTemplate;
        this.checkoutSagaOrchestrator = checkoutSagaOrchestrator;
        this.sagaStateService = sagaStateService;
        this.returnService = returnService;
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
    public OrderDto checkout(Long customerId, CheckoutRequest request) {
        return checkout(customerId, request, null);
    }

    @Override
    public OrderDto checkout(Long customerId, CheckoutRequest request, String idempotencyKey) {
        log.info("Starting checkout for customer ID: {}, PaymentMethod: {}, IdempotencyKey: {}",
                customerId, request.getPaymentMethod(), idempotencyKey);

        // 1. Idempotency Key Handling
        String effectiveIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : null;

        // 2. Check if Saga or Order already exists for this idempotency key BEFORE calling cart-service or checking empty cart
        if (effectiveIdempotencyKey != null) {
            Optional<OrderSagaState> existingSagaOpt = sagaStateService.findByIdempotencyKey(effectiveIdempotencyKey);
            if (existingSagaOpt.isPresent()) {
                return handleExistingSaga(existingSagaOpt.get(), customerId, request);
            }
            Optional<Order> existingOrderOpt = orderRepository.findByIdempotencyKey(effectiveIdempotencyKey);
            if (existingOrderOpt.isPresent()) {
                return resolveExistingOrderResult(existingOrderOpt.get(), customerId, request, effectiveIdempotencyKey);
            }
        }

        String finalIdempotencyKey = (effectiveIdempotencyKey != null)
                ? effectiveIdempotencyKey
                : UUID.randomUUID().toString();

        // 3. Fetch items from Customer's Cart
        ApiResponse<CartDto> cartResponse = cartClient.getCartByCustomerId(customerId);
        if (cartResponse == null || cartResponse.getData() == null || cartResponse.getData().getItems().isEmpty()) {
            throw new InvalidOrderStateException("Shopping cart is empty. Cannot place order.");
        }

        CartDto cart = cartResponse.getData();
        List<CartItemDto> cartItems = cart.getItems();

        // 4. Compute deterministic request fingerprint
        String requestFingerprint = RequestFingerprintUtil.computeFingerprint(customerId, cartItems, request);

        // 5. Fetch shipping address
        String street = request.getShippingStreet();
        String city = request.getShippingCity();
        String state = request.getShippingState();
        String postalCode = request.getShippingPostalCode();
        String country = request.getShippingCountry();

        if (request.getAddressId() != null) {
            try {
                ApiResponse<AddressDto> addressResponse = profileClient.getAddressById(request.getAddressId());
                if (addressResponse != null && addressResponse.getData() != null) {
                    AddressDto addr = addressResponse.getData();
                    street = addr.getStreet();
                    city = addr.getCity();
                    state = addr.getState();
                    postalCode = addr.getPostalCode();
                    country = addr.getCountry();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch address details from ProfileClient: {}", e.getMessage());
            }
        }

        if (street == null || street.trim().isEmpty()) {
            street = "123 Default Street";
            city = "City";
            state = "State";
            postalCode = "12345";
            country = "Country";
        }

        // 6. Create initial Order entity in PENDING_PAYMENT
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomerId(customerId);
        order.setIdempotencyKey(effectiveIdempotencyKey);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShippingStreet(street);
        order.setShippingCity(city);
        order.setShippingState(state);
        order.setShippingPostalCode(postalCode);
        order.setShippingCountry(country);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;
        Long inferredMerchantId = null;

        List<OrderItem> orderItems = new ArrayList<>();
        List<StockReservationItem> reservationItems = new ArrayList<>();

        for (CartItemDto cartItem : cartItems) {
            String productName = cartItem.getProductName();
            BigDecimal unitPrice = cartItem.getUnitPrice();
            try {
                ApiResponse<ProductSnapshotDto> productRes = productClient.getProductById(cartItem.getProductId());
                if (productRes != null && productRes.getData() != null) {
                    productName = productRes.getData().getName();
                    unitPrice = productRes.getData().getPrice();
                    if (inferredMerchantId == null) {
                        inferredMerchantId = productRes.getData().getMerchantId();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve latest product details: {}", e.getMessage());
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setProductName(productName);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);

            reservationItems.add(new StockReservationItem(cartItem.getProductId(), cartItem.getQuantity()));
        }

        order.setMerchantId(inferredMerchantId != null ? inferredMerchantId : 2L);
        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException dive) {
            if (effectiveIdempotencyKey != null && isIdempotencyConstraintViolation(dive)) {
                log.warn("Concurrent duplicate checkout detected on order save for idempotencyKey: [{}]. Handling gracefully.", effectiveIdempotencyKey);
                return handleConcurrentOrderCollision(effectiveIdempotencyKey, customerId, request);
            }
            throw dive;
        }

        // 7. Construct Saga Context and execute Checkout Saga Orchestration
        String sagaId = "SAGA-" + orderNumber + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        CheckoutSagaContext context = new CheckoutSagaContext(
                sagaId,
                finalIdempotencyKey,
                requestFingerprint,
                customerId,
                savedOrder,
                reservationItems,
                request
        );

        try {
            OrderDto result = checkoutSagaOrchestrator.executeSaga(context);
            auditLog("ORDER_CREATED", "ORDER", String.valueOf(result.getId()), "SUCCESS", null,
                    safeMeta(
                            "orderNumber", result.getOrderNumber(),
                            "customerId", customerId,
                            "status", result.getStatus() != null ? result.getStatus().name() : null,
                            "totalAmount", result.getTotalAmount()
                    ),
                    String.valueOf(customerId), null, "ROLE_CUSTOMER", effectiveIdempotencyKey);
            return result;
        } catch (Exception e) {
            auditLog("ORDER_FAILED", "ORDER", orderNumber, "FAILURE", e.getMessage(),
                    safeMeta(
                            "orderNumber", orderNumber,
                            "customerId", customerId,
                            "totalAmount", totalAmount
                    ),
                    String.valueOf(customerId), null, "ROLE_CUSTOMER", effectiveIdempotencyKey);
            throw e;
        }
    }

    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException dive) {
        String msg = dive.getMessage();
        Throwable cause = dive.getRootCause();
        String causeMsg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : "";
        String fullMsg = ((msg != null ? msg : "") + " " + causeMsg).toLowerCase();

        return fullMsg.contains("uk_order_idempotency")
                || fullMsg.contains("idempotency_key")
                || fullMsg.contains("idempotencykey")
                || fullMsg.contains("duplicate entry");
    }

    private OrderDto handleConcurrentOrderCollision(String idempotencyKey, Long customerId, CheckoutRequest request) {
        Order winningOrder = null;
        for (int i = 0; i < 40; i++) {
            Optional<Order> opt = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (opt.isPresent()) {
                winningOrder = opt.get();
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (winningOrder == null) {
            throw new IdempotencyConflictException("Concurrent checkout conflict for idempotency key: " + idempotencyKey);
        }

        return resolveExistingOrderResult(winningOrder, customerId, request, idempotencyKey);
    }

    private OrderDto resolveExistingOrderResult(Order existingOrder, Long customerId, CheckoutRequest request, String idempotencyKey) {
        if (!existingOrder.getCustomerId().equals(customerId)) {
            throw new InvalidOrderStateException("Idempotency key belongs to another customer or request");
        }

        List<CartItemDto> orderItemsAsCart = (existingOrder.getItems() != null)
                ? existingOrder.getItems().stream()
                        .map(i -> new CartItemDto(null, i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                        .collect(Collectors.toList())
                : List.of();

        String computedFingerprint = RequestFingerprintUtil.computeFingerprint(customerId, orderItemsAsCart, request);

        OrderSagaState sagaState = null;
        for (int i = 0; i < 60; i++) {
            Optional<OrderSagaState> sagaOpt = sagaStateService.findByIdempotencyKey(idempotencyKey);
            if (sagaOpt.isPresent()) {
                sagaState = sagaOpt.get();
                if (sagaState.getStatus() == SagaStatus.COMPLETED
                        || sagaState.getStatus() == SagaStatus.FAILED) {
                    break;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (sagaState != null) {
            if (sagaState.getRequestFingerprint() != null && !sagaState.getRequestFingerprint().equals(computedFingerprint)) {
                throw new IdempotencyConflictException("Idempotency key reused with different checkout request parameters");
            }
            if (sagaState.getStatus() == SagaStatus.COMPLETED) {
                Order refreshed = orderRepository.findById(existingOrder.getId()).orElse(existingOrder);
                log.info("Idempotent checkout request: returning completed winning order ID: {}", refreshed.getId());
                return OrderDto.fromEntity(refreshed);
            } else if (sagaState.getStatus() == SagaStatus.FAILED) {
                throw new PaymentException("Previous checkout with this idempotency key failed: " + sagaState.getFailureReason());
            }
        }

        Order refreshed = orderRepository.findById(existingOrder.getId()).orElse(existingOrder);
        log.info("Idempotent checkout request: returning in-progress winning order ID: {}", refreshed.getId());
        return OrderDto.fromEntity(refreshed);
    }

    private OrderDto handleExistingSaga(OrderSagaState existingSaga, Long customerId, CheckoutRequest request) {
        if (!existingSaga.getCustomerId().equals(customerId)) {
            throw new InvalidOrderStateException("Idempotency key belongs to another customer or request");
        }

        Order existingOrder = orderRepository.findById(existingSaga.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + existingSaga.getOrderId()));

        List<CartItemDto> orderItemsAsCart = existingOrder.getItems().stream()
                .map(i -> new CartItemDto(null, i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                .collect(Collectors.toList());

        String computedFingerprint = RequestFingerprintUtil.computeFingerprint(customerId, orderItemsAsCart, request);

        if (existingSaga.getRequestFingerprint() != null && !existingSaga.getRequestFingerprint().equals(computedFingerprint)) {
            throw new IdempotencyConflictException("Idempotency key reused with different checkout request parameters");
        }

        OrderSagaState currentSaga = existingSaga;
        for (int i = 0; i < 60; i++) {
            if (currentSaga.getStatus() == SagaStatus.COMPLETED
                    || currentSaga.getStatus() == SagaStatus.FAILED) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<OrderSagaState> refreshedOpt = sagaStateService.findByIdempotencyKey(existingSaga.getIdempotencyKey());
            if (refreshedOpt.isPresent()) {
                currentSaga = refreshedOpt.get();
            }
        }

        if (currentSaga.getStatus() == SagaStatus.COMPLETED) {
            log.info("Idempotent checkout request: returning already COMPLETED order ID: {}", currentSaga.getOrderId());
            Order refreshed = orderRepository.findById(existingOrder.getId()).orElse(existingOrder);
            return OrderDto.fromEntity(refreshed);
        } else if (currentSaga.getStatus() == SagaStatus.FAILED) {
            throw new PaymentException("Previous checkout with this idempotency key failed: " + currentSaga.getFailureReason());
        } else {
            // Saga is currently in flight or in reconciliation
            Order refreshed = orderRepository.findById(existingOrder.getId()).orElse(existingOrder);
            return OrderDto.fromEntity(refreshed);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Order cannot be cancelled in current state: " + order.getStatus());
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        auditLog("ORDER_CANCELLED", "ORDER", String.valueOf(orderId), "SUCCESS", null,
                safeMeta(
                        "orderNumber", order.getOrderNumber(),
                        "customerId", customerId,
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", OrderStatus.CANCELLED.name()
                ),
                String.valueOf(customerId), null, "ROLE_CUSTOMER", null);

        // Release stock
        List<StockReservationItem> items = order.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            inventoryClient.releaseStock(new StockReservationRequest(order.getOrderNumber(), items));
        } catch (Exception e) {
            log.warn("Failed to release stock on order cancellation: {}", e.getMessage());
        }

        publishOrderEvent(order, "ORDER_CANCELLED", RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY);
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto requestReturn(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new com.eshoppingzone.order.returns.exception.InvalidReturnStateException("Order has no items to return");
        }

        if (order.getItems().size() > 1) {
            throw new com.eshoppingzone.order.returns.exception.InvalidReturnStateException("Order has multiple items. Please use the dedicated Return API POST /api/v1/returns specifying the orderItemId and quantity.");
        }

        OrderItem singleItem = order.getItems().get(0);
        com.eshoppingzone.order.returns.dto.CreateReturnRequest returnRequest = new com.eshoppingzone.order.returns.dto.CreateReturnRequest(
                order.getId(),
                singleItem.getId(),
                singleItem.getQuantity(),
                com.eshoppingzone.order.returns.enums.ReturnReason.OTHER,
                reason != null && !reason.trim().isEmpty() ? reason : "Return requested via legacy endpoint"
        );

        returnService.createReturn(customerId, returnRequest, null);

        Order updated = orderRepository.findById(orderId).orElse(order);
        return OrderDto.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getMerchantOrders(Long merchantId) {
        return orderRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getMerchantOrderById(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId + " for merchant: " + merchantId));
        return OrderDto.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderDto processMerchantOrder(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Only CONFIRMED orders can be processed. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.PROCESSING);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "ORDER_PROCESSING", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto readyForDelivery(Long orderId, Long merchantId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException("Only PROCESSING orders can be marked READY_FOR_DELIVERY");
        }

        order.setStatus(OrderStatus.READY_FOR_DELIVERY);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "READY_FOR_DELIVERY", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto rejectMerchantOrder(Long orderId, Long merchantId, String reason) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException("Order cannot be rejected in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        // Release stock
        List<StockReservationItem> items = order.getItems().stream()
                .map(i -> new StockReservationItem(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
        try {
            inventoryClient.releaseStock(new StockReservationRequest(order.getOrderNumber(), items));
        } catch (Exception e) {
            log.warn("Failed to release stock on order rejection: {}", e.getMessage());
        }

        publishOrderEvent(saved, "ORDER_REJECTED", RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrdersAdmin() {
        return orderRepository.findAll().stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatusAdmin(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.SUCCESS);
            }
        }

        Order saved = orderRepository.save(order);
        auditLog("ORDER_STATUS_CHANGED", "ORDER", String.valueOf(orderId), "SUCCESS", null,
                safeMeta(
                        "orderNumber", order.getOrderNumber(),
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", status != null ? status.name() : null
                ),
                "ADMIN", "admin", "ROLE_ADMIN", null);
        publishOrderEvent(saved, "ORDER_STATUS_UPDATED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatusInternal(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.SUCCESS);
            }
        }

        Order saved = orderRepository.save(order);
        auditLog("ORDER_STATUS_CHANGED", "ORDER", String.valueOf(orderId), "SUCCESS", null,
                safeMeta(
                        "orderNumber", order.getOrderNumber(),
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", status != null ? status.name() : null
                ),
                "INTERNAL", "INTERNAL_SERVICE", "ROLE_INTERNAL", null);
        publishOrderEvent(saved, "ORDER_STATUS_UPDATED", RabbitMQConfig.ORDER_STATUS_ROUTING_KEY);
        return OrderDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrderInternal(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return OrderDto.fromEntity(order);
    }

    private void publishOrderEvent(Order order, String eventType, String routingKey) {
        try {
            OrderEvent event = new OrderEvent(
                    eventType,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    order.getTotalAmount(),
                    order.getStatus().name(),
                    order.getPaymentMethod().name(),
                    order.getPaymentStatus().name()
            );
            CorrelationData correlationData = new CorrelationData("order-" + order.getId() + "-" + eventType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event, correlationData);
        } catch (Exception e) {
            log.warn("Failed to publish order event {}: {}", eventType, e.getMessage());
        }
    }
}
