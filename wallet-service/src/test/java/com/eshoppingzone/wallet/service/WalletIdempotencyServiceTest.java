package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.IdempotencyStatus;
import com.eshoppingzone.wallet.entity.WalletIdempotencyRecord;
import com.eshoppingzone.wallet.exception.IdempotencyConflictException;
import com.eshoppingzone.wallet.repository.WalletIdempotencyRepository;
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
class WalletIdempotencyServiceTest {

    @Mock
    private WalletIdempotencyRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private WalletIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new WalletIdempotencyService(idempotencyRepository, objectMapper);
    }

    @Test
    void testComputeFingerprintsDeterministic() {
        WalletTransferRequest req1 = new WalletTransferRequest(100L, new BigDecimal("150.00"), "TXN-1", "Purchase");
        WalletTransferRequest req2 = new WalletTransferRequest(100L, new BigDecimal("150.00"), "TXN-1", "Purchase");
        WalletTransferRequest req3 = new WalletTransferRequest(100L, new BigDecimal("200.00"), "TXN-1", "Purchase");

        String fp1 = idempotencyService.computeTransferFingerprint(req1);
        String fp2 = idempotencyService.computeTransferFingerprint(req2);
        String fp3 = idempotencyService.computeTransferFingerprint(req3);

        assertEquals(fp1, fp2);
        assertNotEquals(fp1, fp3);

        String topupFp1 = idempotencyService.computeTopUpFingerprint(100L, new BigDecimal("50.00"));
        String topupFp2 = idempotencyService.computeTopUpFingerprint(100L, new BigDecimal("50.00"));
        assertEquals(topupFp1, topupFp2);
    }

    @Test
    void testCheckOrStartProcessing_NewRequest() {
        String key = "WALLET-KEY-1";
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        WalletIdempotencyRecord record = new WalletIdempotencyRecord(key, "WALLET_DEBIT", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.saveAndFlush(any(WalletIdempotencyRecord.class))).thenReturn(record);

        Optional<WalletIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "WALLET_DEBIT", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.PROCESSING, result.get().getStatus());
    }

    @Test
    void testCheckOrStartProcessing_CompletedRequest() {
        String key = "WALLET-KEY-1";
        WalletIdempotencyRecord record = new WalletIdempotencyRecord(key, "WALLET_DEBIT", 100L, "FP1", IdempotencyStatus.COMPLETED);
        record.setResponseBody("{\"userId\":100,\"balance\":450.00}");
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        Optional<WalletIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "WALLET_DEBIT", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCheckOrStartProcessing_TamperedParametersThrowsConflict() {
        String key = "WALLET-KEY-1";
        WalletIdempotencyRecord record = new WalletIdempotencyRecord(key, "WALLET_DEBIT", 100L, "FP-ORIGINAL", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "WALLET_DEBIT", 100L, "FP-TAMPERED"));
    }

    @Test
    void testCheckOrStartProcessing_DifferentUserThrowsConflict() {
        String key = "WALLET-KEY-1";
        WalletIdempotencyRecord record = new WalletIdempotencyRecord(key, "WALLET_DEBIT", 100L, "FP1", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "WALLET_DEBIT", 200L, "FP1"));
    }

    @Test
    void testCheckOrStartProcessing_ConcurrentInsertCollision() {
        String key = "WALLET-KEY-COLLIDE";
        when(idempotencyRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new WalletIdempotencyRecord(key, "WALLET_DEBIT", 100L, "FP1", IdempotencyStatus.COMPLETED)));

        when(idempotencyRepository.saveAndFlush(any(WalletIdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_wallet_idempotency_key"));

        Optional<WalletIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "WALLET_DEBIT", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
    }

    @Test
    void testMarkCompletedAndParse() {
        String key = "KEY-PARSE";
        WalletIdempotencyRecord record = new WalletIdempotencyRecord(key, "WALLET_TOPUP", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        WalletDto dto = new WalletDto(1L, 100L, new BigDecimal("750.00"), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        idempotencyService.markCompleted(key, dto);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertNotNull(record.getResponseBody());

        Optional<WalletDto> parsed = idempotencyService.parseResponseBody(record, WalletDto.class);
        assertTrue(parsed.isPresent());
        assertEquals(new BigDecimal("750.00"), parsed.get().getBalance());
    }
}
