package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransactionDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    WalletDto getWalletByUserId(Long userId);
    BigDecimal getBalance(Long userId);
    WalletDto topUp(Long userId, BigDecimal amount);
    WalletDto topUp(Long userId, BigDecimal amount, String idempotencyKey);
    WalletDto debit(WalletTransferRequest request);
    WalletDto debit(WalletTransferRequest request, String idempotencyKey);
    WalletDto credit(WalletTransferRequest request);
    WalletDto credit(WalletTransferRequest request, String idempotencyKey);
    List<WalletTransactionDto> getTransactions(Long userId);
}
