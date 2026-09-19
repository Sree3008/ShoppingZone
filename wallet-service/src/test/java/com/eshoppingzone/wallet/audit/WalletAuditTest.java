package com.eshoppingzone.wallet.audit;

import com.eshoppingzone.wallet.audit.entity.AuditLog;
import com.eshoppingzone.wallet.audit.service.AuditLogService;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.exception.InsufficientBalanceException;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.repository.WalletTransactionRepository;
import com.eshoppingzone.wallet.service.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletAuditTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private AuditLogService auditLogService;

    private WalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(
                walletRepository,
                transactionRepository,
                null,
                auditLogService
        );
    }

    @Test
    void testTopUpCreatesAuditLog() {
        Wallet wallet = new Wallet(1L, 100L, new BigDecimal("200.00"));
        when(walletRepository.findByUserIdWithLock(100L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        walletService.topUp(100L, new BigDecimal("50.00"));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("WALLET"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("WALLET_TOPUP", actionCaptor.getValue());
    }

    @Test
    void testDebitSuccessCreatesAuditLog() {
        Wallet wallet = new Wallet(1L, 100L, new BigDecimal("200.00"));
        when(walletRepository.findByUserIdWithLock(100L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("50.00"), "TXN-1", "Payment");
        walletService.debit(request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("WALLET"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("WALLET_DEBIT", actionCaptor.getValue());
    }

    @Test
    void testDebitInsufficientBalanceCreatesFailedAuditLog() {
        Wallet wallet = new Wallet(1L, 100L, new BigDecimal("10.00"));
        when(walletRepository.findByUserIdWithLock(100L)).thenReturn(Optional.of(wallet));

        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("50.00"), "TXN-1", "Payment");
        assertThrows(InsufficientBalanceException.class, () -> walletService.debit(request));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outcomeCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("WALLET"),
                eq("1"),
                outcomeCaptor.capture(),
                eq("Insufficient balance"),
                any()
        );

        assertEquals("WALLET_TRANSACTION_FAILED", actionCaptor.getValue());
        assertEquals("FAILURE", outcomeCaptor.getValue());
    }

    @Test
    void testCreditRefundCreatesRefundCreditAuditLog() {
        Wallet wallet = new Wallet(1L, 100L, new BigDecimal("200.00"));
        when(walletRepository.findByUserIdWithLock(100L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        WalletTransferRequest request = new WalletTransferRequest(100L, new BigDecimal("50.00"), "REF-1", "Refund for Order #10");
        walletService.credit(request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("WALLET"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("REFUND_CREDIT", actionCaptor.getValue());
    }

    @Test
    void testAuditLogImmutability() {
        AuditLog log = new AuditLog();
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }
}
