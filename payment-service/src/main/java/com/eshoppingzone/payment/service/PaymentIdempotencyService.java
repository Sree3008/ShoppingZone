package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.entity.IdempotencyStatus;
import com.eshoppingzone.payment.entity.PaymentIdempotencyRecord;
import com.eshoppingzone.payment.exception.IdempotencyConflictException;
import com.eshoppingzone.payment.repository.PaymentIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PaymentIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIdempotencyService.class);

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public PaymentIdempotencyService(PaymentIdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public String computePaymentFingerprint(ProcessPaymentRequest request) {
        String raw = "orderId=" + (request.getOrderId() != null ? request.getOrderId() : "")
                + "|customerId=" + (request.getCustomerId() != null ? request.getCustomerId() : "")
                + "|amount=" + (request.getAmount() != null ? request.getAmount().stripTrailingZeros().toPlainString() : "")
                + "|paymentMethod=" + (request.getPaymentMethod() != null ? request.getPaymentMethod().name() : "");
        return sha256(raw);
    }

    public String computeRefundFingerprint(Long customerId, RefundRequest request) {
        String raw = "orderId=" + (request.getOrderId() != null ? request.getOrderId() : "")
                + "|customerId=" + (customerId != null ? customerId : "")
                + "|amount=" + (request.getAmount() != null ? request.getAmount().stripTrailingZeros().toPlainString() : "")
                + "|reason=" + (request.getReason() != null ? request.getReason().trim() : "");
        return sha256(raw);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentIdempotencyRecord> checkOrStartProcessing(String idempotencyKey,
                                                                    String operation,
                                                                    Long userId,
                                                                    String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<PaymentIdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            PaymentIdempotencyRecord record = existingOpt.get();
            validateExistingRecord(record, userId, requestFingerprint);
            return Optional.of(record);
        }

        PaymentIdempotencyRecord newRecord = new PaymentIdempotencyRecord(
                idempotencyKey,
                operation,
                userId,
                requestFingerprint,
                IdempotencyStatus.PROCESSING
        );

        try {
            PaymentIdempotencyRecord saved = idempotencyRepository.saveAndFlush(newRecord);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException dive) {
            log.info("Concurrent insert collision for idempotency key: {}", idempotencyKey);
            PaymentIdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException("Concurrent conflict accessing idempotency record"));
            validateExistingRecord(winner, userId, requestFingerprint);
            return Optional.of(winner);
        }
    }

    private void validateExistingRecord(PaymentIdempotencyRecord record, Long userId, String requestFingerprint) {
        if (record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new IdempotencyConflictException("Idempotency key belongs to another customer or request");
        }
        if (record.getRequestFingerprint() != null && !record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException("Idempotency key reused with different request parameters");
        }
        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new IdempotencyConflictException("A request with this idempotency key is currently processing");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String idempotencyKey, Object responseDto) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }
        idempotencyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            try {
                record.setResponseBody(objectMapper.writeValueAsString(responseDto));
            } catch (Exception e) {
                log.warn("Failed to serialize response body for idempotency key {}: {}", idempotencyKey, e.getMessage());
            }
            idempotencyRepository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey, String errorMessage) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }
        idempotencyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            if (errorMessage != null && errorMessage.length() > 500) {
                record.setErrorMessage(errorMessage.substring(0, 500));
            } else {
                record.setErrorMessage(errorMessage);
            }
            idempotencyRepository.save(record);
        });
    }

    public <T> Optional<T> parseResponseBody(PaymentIdempotencyRecord record, Class<T> clazz) {
        if (record == null || record.getResponseBody() == null || record.getStatus() != IdempotencyStatus.COMPLETED) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.getResponseBody(), clazz));
        } catch (Exception e) {
            log.error("Failed to parse cached idempotency response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
