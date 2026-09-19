package com.eshoppingzone.payment.service;

import com.eshoppingzone.payment.dto.PaymentDto;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.entity.IdempotencyStatus;
import com.eshoppingzone.payment.entity.PaymentIdempotencyRecord;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.exception.IdempotencyConflictException;
import com.eshoppingzone.payment.repository.PaymentIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyServiceTest {

    @Mock
    private PaymentIdempotencyRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private PaymentIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new PaymentIdempotencyService(idempotencyRepository, objectMapper);
    }

    @Test
    void testFingerprintDeterministic() {
        ProcessPaymentRequest req1 = new ProcessPaymentRequest(10L, 100L, new BigDecimal("50.00"), PaymentMethod.WALLET);
        ProcessPaymentRequest req2 = new ProcessPaymentRequest(10L, 100L, new BigDecimal("50.00"), PaymentMethod.WALLET);
        ProcessPaymentRequest req3 = new ProcessPaymentRequest(10L, 100L, new BigDecimal("99.99"), PaymentMethod.WALLET);

        String fp1 = idempotencyService.computePaymentFingerprint(req1);
        String fp2 = idempotencyService.computePaymentFingerprint(req2);
        String fp3 = idempotencyService.computePaymentFingerprint(req3);

        assertEquals(fp1, fp2);
        assertNotEquals(fp1, fp3);
    }

    @Test
    void testCheckOrStartProcessing_NewRequest() {
        String key = "KEY-123";
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        PaymentIdempotencyRecord savedRecord = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.saveAndFlush(any(PaymentIdempotencyRecord.class))).thenReturn(savedRecord);

        Optional<PaymentIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.PROCESSING, result.get().getStatus());
        verify(idempotencyRepository).saveAndFlush(any(PaymentIdempotencyRecord.class));
    }

    @Test
    void testCheckOrStartProcessing_CompletedRequest() {
        String key = "KEY-123";
        PaymentIdempotencyRecord existing = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.COMPLETED);
        existing.setResponseBody("{\"id\":1,\"orderId\":10}");
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Optional<PaymentIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCheckOrStartProcessing_ParameterMismatch_ThrowsConflict() {
        String key = "KEY-123";
        PaymentIdempotencyRecord existing = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP-ORIGINAL", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 100L, "FP-TAMPERED"));
    }

    @Test
    void testCheckOrStartProcessing_UserMismatch_ThrowsConflict() {
        String key = "KEY-123";
        PaymentIdempotencyRecord existing = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 999L, "FP1"));
    }

    @Test
    void testCheckOrStartProcessing_ConcurrentProcessing_ThrowsConflict() {
        String key = "KEY-123";
        PaymentIdempotencyRecord existing = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 100L, "FP1"));
    }

    @Test
    void testCheckOrStartProcessing_RaceConditionCollision() {
        String key = "KEY-RACE";
        when(idempotencyRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty()) // First lookup before save: none found
                .thenReturn(Optional.of(new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.COMPLETED))); // Winner record

        when(idempotencyRepository.saveAndFlush(any(PaymentIdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_payment_idempotency_key"));

        Optional<PaymentIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "PAYMENT_PROCESS", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
    }

    @Test
    void testMarkCompletedAndParseBody() {
        String key = "KEY-PARSE";
        PaymentIdempotencyRecord record = new PaymentIdempotencyRecord(key, "PAYMENT_PROCESS", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        PaymentDto dto = new PaymentDto();
        dto.setId(42L);
        dto.setOrderId(100L);
        dto.setAmount(new BigDecimal("99.00"));

        idempotencyService.markCompleted(key, dto);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertNotNull(record.getResponseBody());

        Optional<PaymentDto> parsed = idempotencyService.parseResponseBody(record, PaymentDto.class);
        assertTrue(parsed.isPresent());
        assertEquals(42L, parsed.get().getId());
        assertEquals(100L, parsed.get().getOrderId());
    }
}
