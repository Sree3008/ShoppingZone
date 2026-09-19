package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.IdempotencyStatus;
import com.eshoppingzone.wallet.entity.WalletIdempotencyRecord;
import com.eshoppingzone.wallet.exception.IdempotencyConflictException;
import com.eshoppingzone.wallet.repository.WalletIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class WalletIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(WalletIdempotencyService.class);

    private final WalletIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public WalletIdempotencyService(WalletIdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public String computeTopUpFingerprint(Long userId, BigDecimal amount) {
        String raw = "userId=" + (userId != null ? userId : "")
                + "|amount=" + (amount != null ? amount.stripTrailingZeros().toPlainString() : "");
        return sha256(raw);
    }

    public String computeTransferFingerprint(WalletTransferRequest request) {
        String raw = "userId=" + (request.getUserId() != null ? request.getUserId() : "")
                + "|amount=" + (request.getAmount() != null ? request.getAmount().stripTrailingZeros().toPlainString() : "")
                + "|reference=" + (request.getReference() != null ? request.getReference().trim() : "");
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
    public Optional<WalletIdempotencyRecord> checkOrStartProcessing(String idempotencyKey,
                                                                   String operation,
                                                                   Long userId,
                                                                   String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return Optional.empty();
        }

        Optional<WalletIdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            WalletIdempotencyRecord record = existingOpt.get();
            validateExistingRecord(record, userId, requestFingerprint);
            return Optional.of(record);
        }

        WalletIdempotencyRecord newRecord = new WalletIdempotencyRecord(
                idempotencyKey,
                operation,
                userId,
                requestFingerprint,
                IdempotencyStatus.PROCESSING
        );

        try {
            WalletIdempotencyRecord saved = idempotencyRepository.saveAndFlush(newRecord);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException dive) {
            log.info("Concurrent insert collision in wallet idempotency key: {}", idempotencyKey);
            WalletIdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException("Concurrent conflict accessing wallet idempotency record"));
            validateExistingRecord(winner, userId, requestFingerprint);
            return Optional.of(winner);
        }
    }

    private void validateExistingRecord(WalletIdempotencyRecord record, Long userId, String requestFingerprint) {
        if (record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new IdempotencyConflictException("Idempotency key belongs to another user or request");
        }
        if (record.getRequestFingerprint() != null && !record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException("Idempotency key reused with different transaction parameters");
        }
        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new IdempotencyConflictException("A wallet operation with this idempotency key is currently processing");
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
                log.warn("Failed to serialize wallet response body for key {}: {}", idempotencyKey, e.getMessage());
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

    public <T> Optional<T> parseResponseBody(WalletIdempotencyRecord record, Class<T> clazz) {
        if (record == null || record.getResponseBody() == null || record.getStatus() != IdempotencyStatus.COMPLETED) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.getResponseBody(), clazz));
        } catch (Exception e) {
            log.error("Failed to parse cached wallet response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
