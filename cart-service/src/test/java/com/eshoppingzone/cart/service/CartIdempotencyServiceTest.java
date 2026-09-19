package com.eshoppingzone.cart.service;

import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.entity.CartIdempotencyRecord;
import com.eshoppingzone.cart.entity.IdempotencyStatus;
import com.eshoppingzone.cart.exception.IdempotencyConflictException;
import com.eshoppingzone.cart.repository.CartIdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartIdempotencyServiceTest {

    @Mock
    private CartIdempotencyRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private CartIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new CartIdempotencyService(idempotencyRepository, objectMapper);
    }

    @Test
    void testComputeFingerprintDeterministic() {
        AddToCartRequest req1 = new AddToCartRequest(10L, 2);
        AddToCartRequest req2 = new AddToCartRequest(10L, 2);
        AddToCartRequest req3 = new AddToCartRequest(10L, 5);

        String fp1 = idempotencyService.computeAddToCartFingerprint(100L, req1);
        String fp2 = idempotencyService.computeAddToCartFingerprint(100L, req2);
        String fp3 = idempotencyService.computeAddToCartFingerprint(100L, req3);

        assertEquals(fp1, fp2);
        assertNotEquals(fp1, fp3);
    }

    @Test
    void testCheckOrStartProcessing_NewRequest() {
        String key = "CART-KEY-1";
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        CartIdempotencyRecord record = new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.saveAndFlush(any(CartIdempotencyRecord.class))).thenReturn(record);

        Optional<CartIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "ADD_TO_CART", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.PROCESSING, result.get().getStatus());
    }

    @Test
    void testCheckOrStartProcessing_CompletedReturnsCached() {
        String key = "CART-KEY-1";
        CartIdempotencyRecord record = new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP1", IdempotencyStatus.COMPLETED);
        record.setResponseBody("{\"id\":1,\"customerId\":100,\"items\":[]}");
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        Optional<CartIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "ADD_TO_CART", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCheckOrStartProcessing_TamperedParamsThrowsConflict() {
        String key = "CART-KEY-1";
        CartIdempotencyRecord record = new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP-ORIG", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "ADD_TO_CART", 100L, "FP-ALTERED"));
    }

    @Test
    void testCheckOrStartProcessing_DifferentUserThrowsConflict() {
        String key = "CART-KEY-1";
        CartIdempotencyRecord record = new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP1", IdempotencyStatus.COMPLETED);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.checkOrStartProcessing(key, "ADD_TO_CART", 200L, "FP1"));
    }

    @Test
    void testCheckOrStartProcessing_ConcurrentInsertCollision() {
        String key = "CART-KEY-RACE";
        when(idempotencyRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP1", IdempotencyStatus.COMPLETED)));

        when(idempotencyRepository.saveAndFlush(any(CartIdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_cart_idempotency_key"));

        Optional<CartIdempotencyRecord> result = idempotencyService.checkOrStartProcessing(key, "ADD_TO_CART", 100L, "FP1");

        assertTrue(result.isPresent());
        assertEquals(IdempotencyStatus.COMPLETED, result.get().getStatus());
    }

    @Test
    void testMarkCompletedAndParse() {
        String key = "KEY-PARSE";
        CartIdempotencyRecord record = new CartIdempotencyRecord(key, "ADD_TO_CART", 100L, "FP1", IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        CartDto dto = new CartDto(1L, 100L, Collections.emptyList(), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        idempotencyService.markCompleted(key, dto);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertNotNull(record.getResponseBody());

        Optional<CartDto> parsed = idempotencyService.parseResponseBody(record, CartDto.class);
        assertTrue(parsed.isPresent());
        assertEquals(1L, parsed.get().getId());
        assertEquals(100L, parsed.get().getCustomerId());
    }
}
