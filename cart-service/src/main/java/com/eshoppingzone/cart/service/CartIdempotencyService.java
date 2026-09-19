package com.eshoppingzone.cart.service;

import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.entity.CartIdempotencyRecord;
import com.eshoppingzone.cart.entity.IdempotencyStatus;
import com.eshoppingzone.cart.exception.IdempotencyConflictException;
import com.eshoppingzone.cart.repository.CartIdempotencyRepository;
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
public class CartIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(CartIdempotencyService.class);

    private final CartIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public CartIdempotencyService(CartIdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public String computeAddToCartFingerprint(Long customerId, AddToCartRequest request) {
        String raw = "customerId=" + (customerId != null ? customerId : "")
                + "|productId=" + (request != null && request.getProductId() != null ? request.getProductId() : "")
                + "|quantity=" + (request != null && request.getQuantity() != null ? request.getQuantity() : "");
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
    public Optional<CartIdempotencyRecord> checkOrStartProcessing(String idempotencyKey,
                                                                 String operation,
                                                                 Long userId,
                                                                 String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<CartIdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            CartIdempotencyRecord record = existingOpt.get();
            validateExistingRecord(record, userId, requestFingerprint);
            return Optional.of(record);
        }

        CartIdempotencyRecord newRecord = new CartIdempotencyRecord(
                idempotencyKey,
                operation,
                userId,
                requestFingerprint,
                IdempotencyStatus.PROCESSING
        );

        try {
            CartIdempotencyRecord saved = idempotencyRepository.saveAndFlush(newRecord);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException dive) {
            log.info("Concurrent insert collision in cart idempotency key: {}", idempotencyKey);
            CartIdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException("Concurrent conflict accessing cart idempotency record"));
            validateExistingRecord(winner, userId, requestFingerprint);
            return Optional.of(winner);
        }
    }

    private void validateExistingRecord(CartIdempotencyRecord record, Long userId, String requestFingerprint) {
        if (record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new IdempotencyConflictException("Idempotency key belongs to another customer or cart");
        }
        if (record.getRequestFingerprint() != null && !record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException("Idempotency key reused with different item parameters");
        }
        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new IdempotencyConflictException("A cart update with this idempotency key is currently processing");
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
                log.warn("Failed to serialize cart response body for key {}: {}", idempotencyKey, e.getMessage());
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

    public <T> Optional<T> parseResponseBody(CartIdempotencyRecord record, Class<T> clazz) {
        if (record == null || record.getResponseBody() == null || record.getStatus() != IdempotencyStatus.COMPLETED) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.getResponseBody(), clazz));
        } catch (Exception e) {
            log.error("Failed to parse cached cart response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
