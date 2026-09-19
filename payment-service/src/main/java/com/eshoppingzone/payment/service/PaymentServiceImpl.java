package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.client.OrderClient;
import com.eshoppingzone.payment.client.WalletClient;
import com.eshoppingzone.payment.config.RabbitMQConfig;
import com.eshoppingzone.payment.dto.*;
import com.eshoppingzone.payment.entity.*;
import com.eshoppingzone.payment.exception.InvalidRefundException;
import com.eshoppingzone.payment.exception.PaymentException;
import com.eshoppingzone.payment.exception.ResourceNotFoundException;
import com.eshoppingzone.payment.repository.PaymentRepository;
import com.eshoppingzone.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Long PLATFORM_WALLET_USER_ID = 1L; // Admin / Platform Wallet

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletClient walletClient;
    private final OrderClient orderClient;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentIdempotencyService idempotencyService;
    private final com.eshoppingzone.payment.audit.service.AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              RefundRepository refundRepository,
                              WalletClient walletClient,
                              OrderClient orderClient,
                              RabbitTemplate rabbitTemplate,
                              PaymentIdempotencyService idempotencyService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.payment.audit.service.AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.walletClient = walletClient;
        this.orderClient = orderClient;
        this.rabbitTemplate = rabbitTemplate;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              RefundRepository refundRepository,
                              WalletClient walletClient,
                              OrderClient orderClient,
                              RabbitTemplate rabbitTemplate,
                              PaymentIdempotencyService idempotencyService) {
        this(paymentRepository, refundRepository, walletClient, orderClient, rabbitTemplate, idempotencyService, null);
    }

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              RefundRepository refundRepository,
                              WalletClient walletClient,
                              OrderClient orderClient,
                              RabbitTemplate rabbitTemplate) {
        this(paymentRepository, refundRepository, walletClient, orderClient, rabbitTemplate, null, null);
    }

    private Map<String, Object> safeMeta(Object... keyValues) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length && keyValues[i] != null && keyValues[i + 1] != null) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }

    private void audit(String action, String resourceType, String resourceId, String outcome, String failureReason, Map<String, Object> metadata) {
        if (auditLogService != null) {
            try {
                auditLogService.logAction(action, resourceType, resourceId, outcome, failureReason, metadata);
            } catch (Exception e) {
                log.warn("Failed to write audit log: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentDto processPayment(ProcessPaymentRequest request) {
        return processPayment(request, request != null ? request.getIdempotencyKey() : null);
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentDto processPayment(ProcessPaymentRequest request, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : (request != null ? request.getIdempotencyKey() : null);

        if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
            String fingerprint = idempotencyService.computePaymentFingerprint(request);
            Optional<PaymentIdempotencyRecord> recordOpt = idempotencyService.checkOrStartProcessing(
                    effectiveKey, "PAYMENT_PROCESS", request.getCustomerId(), fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == IdempotencyStatus.COMPLETED) {
                Optional<PaymentDto> cachedDto = idempotencyService.parseResponseBody(recordOpt.get(), PaymentDto.class);
                if (cachedDto.isPresent()) {
                    log.info("Returning cached idempotent payment response for key: {}", effectiveKey);
                    return cachedDto.get();
                }
            }
        }

        try {
            PaymentDto result = executeProcessPayment(request, effectiveKey);
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
                idempotencyService.markCompleted(effectiveKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()
                    && !(e instanceof com.eshoppingzone.payment.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(effectiveKey, e.getMessage());
            }
            throw e;
        }
    }

    private PaymentDto executeProcessPayment(ProcessPaymentRequest request, String effectiveKey) {
        log.info("Processing payment for orderId: {}, customerId: {}, amount: {}, method: {}",
                request.getOrderId(), request.getCustomerId(), request.getAmount(), request.getPaymentMethod());

        // Idempotency check: if payment already exists for this order
        Optional<Payment> existingPaymentOpt = paymentRepository.findByOrderId(request.getOrderId());
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                audit("PAYMENT_SUCCESS_REPLAY", "PAYMENT", String.valueOf(existing.getId()), "SUCCESS", null,
                        safeMeta("orderId", existing.getOrderId(), "amount", existing.getAmount(), "paymentMethod", existing.getPaymentMethod()));
                return PaymentDto.fromEntity(existing);
            }
        }

        Payment payment = existingPaymentOpt.orElseGet(Payment::new);
        payment.setOrderId(request.getOrderId());
        payment.setCustomerId(request.getCustomerId());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());

        String txnRef = (effectiveKey != null && !effectiveKey.isBlank())
                ? ("TXN-" + effectiveKey)
                : ("TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        payment.setTransactionReference(txnRef);

        if (request.getPaymentMethod() == PaymentMethod.WALLET) {
            try {
                // 1. Debit Customer Wallet
                WalletTransferRequest debitReq = new WalletTransferRequest(
                        request.getCustomerId(),
                        request.getAmount(),
                        txnRef,
                        "Payment for Order #" + request.getOrderId()
                );
                ApiResponse<WalletDto> debitRes = walletClient.debit(debitReq);
                if (debitRes == null || !debitRes.isSuccess()) {
                    payment.setStatus(PaymentStatus.FAILED);
                    Payment saved = paymentRepository.save(payment);
                    publishPaymentEvent(saved, "PAYMENT_FAILED", RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY);
                    audit("PAYMENT_FAILED", "PAYMENT", String.valueOf(saved.getId()), "FAILURE", "Wallet debit failed: insufficient funds or wallet unavailable",
                            safeMeta("orderId", request.getOrderId(), "amount", request.getAmount(), "paymentMethod", "WALLET", "customerId", request.getCustomerId()));
                    throw new PaymentException("Wallet debit failed: insufficient funds or wallet unavailable");
                }

                // 2. Credit Platform Wallet
                WalletTransferRequest creditReq = new WalletTransferRequest(
                        PLATFORM_WALLET_USER_ID,
                        request.getAmount(),
                        txnRef,
                        "Revenue from Order #" + request.getOrderId()
                );
                walletClient.credit(creditReq);

                payment.setStatus(PaymentStatus.SUCCESS);
                Payment saved = paymentRepository.save(payment);
                publishPaymentEvent(saved, "PAYMENT_SUCCESS", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY);
                audit("PAYMENT_SUCCESS", "PAYMENT", String.valueOf(saved.getId()), "SUCCESS", null,
                        safeMeta("orderId", request.getOrderId(), "amount", request.getAmount(), "paymentMethod", "WALLET", "transactionReference", txnRef, "customerId", request.getCustomerId()));
                return PaymentDto.fromEntity(saved);

            } catch (PaymentException pe) {
                throw pe;
            } catch (Exception e) {
                payment.setStatus(PaymentStatus.FAILED);
                Payment saved = paymentRepository.save(payment);
                publishPaymentEvent(saved, "PAYMENT_FAILED", RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY);
                audit("PAYMENT_FAILED", "PAYMENT", String.valueOf(saved.getId()), "FAILURE", e.getMessage(),
                        safeMeta("orderId", request.getOrderId(), "amount", request.getAmount(), "paymentMethod", "WALLET", "customerId", request.getCustomerId()));
                throw new PaymentException("Payment error: " + e.getMessage());
            }
        } else if (request.getPaymentMethod() == PaymentMethod.COD) {
            payment.setStatus(PaymentStatus.PENDING);
            Payment saved = paymentRepository.save(payment);
            audit("PAYMENT_PENDING", "PAYMENT", String.valueOf(saved.getId()), "SUCCESS", null,
                    safeMeta("orderId", request.getOrderId(), "amount", request.getAmount(), "paymentMethod", "COD", "customerId", request.getCustomerId()));
            return PaymentDto.fromEntity(saved);
        }

        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.save(payment);
        audit("PAYMENT_FAILED", "PAYMENT", String.valueOf(saved.getId()), "FAILURE", "Unsupported payment method",
                safeMeta("orderId", request.getOrderId(), "amount", request.getAmount(), "paymentMethod", request.getPaymentMethod()));
        return PaymentDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDto getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order ID: " + orderId));
        return PaymentDto.fromEntity(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDto> getMyPayments(Long customerId) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(PaymentDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentDto completeCodPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order ID: " + orderId));

        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            payment.setStatus(PaymentStatus.SUCCESS);
            Payment saved = paymentRepository.save(payment);

            // Credit Platform Wallet for COD collection
            try {
                WalletTransferRequest creditReq = new WalletTransferRequest(
                        PLATFORM_WALLET_USER_ID,
                        payment.getAmount(),
                        payment.getTransactionReference(),
                        "COD collection for Order #" + orderId
                );
                walletClient.credit(creditReq);
            } catch (Exception e) {
                log.warn("Failed to credit platform wallet for COD: {}", e.getMessage());
            }

            publishPaymentEvent(saved, "PAYMENT_SUCCESS", RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY);
            audit("PAYMENT_COD_COMPLETED", "PAYMENT", String.valueOf(saved.getId()), "SUCCESS", null,
                    safeMeta("orderId", orderId, "amount", saved.getAmount(), "paymentMethod", "COD", "customerId", saved.getCustomerId()));
            return PaymentDto.fromEntity(saved);
        }

        return PaymentDto.fromEntity(payment);
    }

    @Override
    @Transactional
    public RefundDto requestRefund(Long customerId, RefundRequest request) {
        return requestRefund(customerId, request, request != null ? request.getIdempotencyKey() : null);
    }

    @Override
    @Transactional
    public RefundDto requestRefund(Long customerId, RefundRequest request, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : (request != null ? request.getIdempotencyKey() : null);

        if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
            String fingerprint = idempotencyService.computeRefundFingerprint(customerId, request);
            Optional<PaymentIdempotencyRecord> recordOpt = idempotencyService.checkOrStartProcessing(
                    effectiveKey, "REFUND_REQUEST", customerId, fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == IdempotencyStatus.COMPLETED) {
                Optional<RefundDto> cachedDto = idempotencyService.parseResponseBody(recordOpt.get(), RefundDto.class);
                if (cachedDto.isPresent()) {
                    log.info("Returning cached idempotent refund response for key: {}", effectiveKey);
                    return cachedDto.get();
                }
            }
        }

        try {
            RefundDto result = executeRequestRefund(customerId, request);
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
                idempotencyService.markCompleted(effectiveKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()
                    && !(e instanceof com.eshoppingzone.payment.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(effectiveKey, e.getMessage());
            }
            throw e;
        }
    }

    private RefundDto executeRequestRefund(Long customerId, RefundRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order ID: " + request.getOrderId()));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidRefundException("Refund can only be requested for completed payments");
        }

        // Verify that the order status is RETURNED
        try {
            ApiResponse<OrderDto> orderResponse = orderClient.getOrderInternal(request.getOrderId());
            if (orderResponse == null || orderResponse.getData() == null || !"RETURNED".equalsIgnoreCase(orderResponse.getData().getStatus())) {
                throw new InvalidRefundException("Refund can only be requested for returned orders");
            }
        } catch (InvalidRefundException ire) {
            throw ire;
        } catch (Exception e) {
            log.error("Failed to verify order status for refund on order {}: {}", request.getOrderId(), e.getMessage());
            throw new InvalidRefundException("Refund can only be requested for returned orders");
        }

        String refNum = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(request.getOrderId());
        refund.setCustomerId(customerId);
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason() != null ? request.getReason() : "Customer requested refund");
        refund.setStatus(RefundStatus.PENDING);
        refund.setRefundReference(refNum);

        Refund saved = refundRepository.save(refund);
        log.info("Refund request created: {} for orderId: {}", refNum, request.getOrderId());
        audit("REFUND_REQUESTED", "REFUND", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta("orderId", request.getOrderId(), "paymentId", payment.getId(), "amount", request.getAmount(), "refundReference", refNum, "customerId", customerId));
        return RefundDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public RefundDto requestRefundInternal(RefundRequest request) {
        return requestRefundInternal(request, request != null ? request.getIdempotencyKey() : null);
    }

    @Override
    @Transactional
    public RefundDto requestRefundInternal(RefundRequest request, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : (request != null ? request.getIdempotencyKey() : null);

        Payment payment = paymentRepository.findByOrderId(request.getOrderId()).orElse(null);
        Long customerId = payment != null ? payment.getCustomerId() : 0L;

        if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
            String fingerprint = idempotencyService.computeRefundFingerprint(customerId, request);
            Optional<PaymentIdempotencyRecord> recordOpt = idempotencyService.checkOrStartProcessing(
                    effectiveKey, "REFUND_INTERNAL", customerId, fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == IdempotencyStatus.COMPLETED) {
                Optional<RefundDto> cachedDto = idempotencyService.parseResponseBody(recordOpt.get(), RefundDto.class);
                if (cachedDto.isPresent()) {
                    log.info("Returning cached idempotent internal refund response for key: {}", effectiveKey);
                    return cachedDto.get();
                }
            }
        }

        try {
            RefundDto result = executeRequestRefundInternal(request);
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
                idempotencyService.markCompleted(effectiveKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()
                    && !(e instanceof com.eshoppingzone.payment.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(effectiveKey, e.getMessage());
            }
            throw e;
        }
    }

    private RefundDto executeRequestRefundInternal(RefundRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order ID: " + request.getOrderId()));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidRefundException("Refund can only be requested for completed payments");
        }

        // Verify that the order status is RETURNED
        try {
            ApiResponse<OrderDto> orderResponse = orderClient.getOrderInternal(request.getOrderId());
            if (orderResponse == null || orderResponse.getData() == null || !"RETURNED".equalsIgnoreCase(orderResponse.getData().getStatus())) {
                throw new InvalidRefundException("Refund can only be requested for returned orders");
            }
        } catch (InvalidRefundException ire) {
            throw ire;
        } catch (Exception e) {
            log.error("Failed to verify order status for refund on order {}: {}", request.getOrderId(), e.getMessage());
            throw new InvalidRefundException("Refund can only be requested for returned orders");
        }

        // Idempotency / duplicate protection: check if an active refund (PENDING or APPROVED) already exists for this order/return
        List<Refund> existingRefunds = refundRepository.findByOrderIdOrderByCreatedAtDesc(request.getOrderId());
        for (Refund existing : existingRefunds) {
            if (existing.getStatus() == RefundStatus.PENDING || existing.getStatus() == RefundStatus.APPROVED) {
                boolean isSameReason = Objects.equals(existing.getReason(), request.getReason());
                boolean isSameReturn = request.getReason() != null && existing.getReason() != null
                        && request.getReason().startsWith("Return #")
                        && existing.getReason().startsWith("Return #")
                        && request.getReason().split(":")[0].trim().equals(existing.getReason().split(":")[0].trim());
                if (isSameReason || isSameReturn) {
                    log.warn("Duplicate refund request detected for order {} and reason '{}'. Returning existing refund: {}.",
                            request.getOrderId(), request.getReason(), existing.getRefundReference());
                    audit("REFUND_REQUEST_REPLAY", "REFUND", String.valueOf(existing.getId()), "SUCCESS", null,
                            safeMeta("orderId", request.getOrderId(), "refundReference", existing.getRefundReference()));
                    return RefundDto.fromEntity(existing);
                }
            }
        }

        String refNum = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(request.getOrderId());
        refund.setCustomerId(payment.getCustomerId());
        refund.setAmount(request.getAmount());
        refund.setReason(request.getReason() != null ? request.getReason() : "Order return refund");
        refund.setStatus(RefundStatus.PENDING);
        refund.setRefundReference(refNum);

        Refund saved = refundRepository.save(refund);
        log.info("Internal refund request created: {} for orderId: {}", refNum, request.getOrderId());
        audit("REFUND_INITIATED", "REFUND", String.valueOf(saved.getId()), "SUCCESS", null,
                safeMeta("orderId", request.getOrderId(), "paymentId", payment.getId(), "amount", request.getAmount(), "refundReference", refNum, "customerId", payment.getCustomerId()));
        return RefundDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDto> getPendingRefunds() {
        return refundRepository.findByStatusOrderByCreatedAtDesc(RefundStatus.PENDING).stream()
                .map(RefundDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RefundDto approveRefund(Long refundId) {
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found with id: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new InvalidRefundException("Refund is not in PENDING state. Current status: " + refund.getStatus());
        }

        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Associated payment not found"));

        // 1. Debit Platform Wallet
        WalletTransferRequest debitPlatform = new WalletTransferRequest(
                PLATFORM_WALLET_USER_ID,
                refund.getAmount(),
                refund.getRefundReference(),
                "Refund for Order #" + refund.getOrderId()
        );
        walletClient.debit(debitPlatform);

        // 2. Credit Customer Wallet
        WalletTransferRequest creditCustomer = new WalletTransferRequest(
                refund.getCustomerId(),
                refund.getAmount(),
                refund.getRefundReference(),
                "Refund approved for Order #" + refund.getOrderId()
        );
        walletClient.credit(creditCustomer);

        refund.setStatus(RefundStatus.APPROVED);
        Refund savedRefund = refundRepository.save(refund);

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // Update order status to REFUNDED
        try {
            orderClient.updateOrderStatusInternal(refund.getOrderId(), Map.of("status", "REFUNDED", "note", "Refund approved"));
        } catch (Exception e) {
            log.warn("Failed to notify order service of refund: {}", e.getMessage());
        }

        publishPaymentEvent(payment, "REFUND_COMPLETED", RabbitMQConfig.REFUND_COMPLETED_ROUTING_KEY);
        log.info("Refund approved and processed for refundId: {}, amount: {}", refundId, refund.getAmount());
        audit("REFUND_APPROVED", "REFUND", String.valueOf(refundId), "SUCCESS", null,
                safeMeta("orderId", refund.getOrderId(), "paymentId", payment.getId(), "amount", refund.getAmount(), "refundReference", refund.getRefundReference()));
        return RefundDto.fromEntity(savedRefund);
    }

    @Override
    @Transactional
    public RefundDto rejectRefund(Long refundId, String reason) {
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found with id: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new InvalidRefundException("Refund is not in PENDING state");
        }

        refund.setStatus(RefundStatus.REJECTED);
        refund.setReason(reason != null ? refund.getReason() + " (Rejected: " + reason + ")" : refund.getReason());
        Refund saved = refundRepository.save(refund);
        audit("REFUND_REJECTED", "REFUND", String.valueOf(refundId), "SUCCESS", null,
                safeMeta("orderId", refund.getOrderId(), "paymentId", refund.getPaymentId(), "reason", reason));
        return RefundDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDto> getMyRefunds(Long customerId) {
        return refundRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(RefundDto::fromEntity)
                .collect(Collectors.toList());
    }

    private void publishPaymentEvent(Payment payment, String eventType, String routingKey) {
        try {
            PaymentEvent event = new PaymentEvent(
                    eventType,
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getPaymentMethod().name(),
                    payment.getStatus().name(),
                    payment.getTransactionReference()
            );
            CorrelationData correlationData = new CorrelationData("payment-" + payment.getId() + "-" + eventType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event, correlationData);
        } catch (Exception e) {
            log.warn("Failed to publish payment event {}: {}", eventType, e.getMessage());
        }
    }
}
