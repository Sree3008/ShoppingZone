package com.eshoppingzone.wallet.controller;

import com.eshoppingzone.wallet.dto.*;
import com.eshoppingzone.wallet.security.UserPrincipal;
import com.eshoppingzone.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet Management", description = "APIs for internal customer and platform wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get My Wallet", description = "Retrieve wallet details and current balance")
    public ResponseEntity<ApiResponse<WalletDto>> getMyWallet(Authentication authentication) {
        Long userId = getUserId(authentication);
        WalletDto wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get Balance", description = "Check current available balance in user wallet")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(Authentication authentication) {
        Long userId = getUserId(authentication);
        BigDecimal balance = walletService.getBalance(userId);
        return ResponseEntity.ok(ApiResponse.success("Balance retrieved successfully", balance));
    }

    @PostMapping("/topup")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Top Up Wallet", description = "Simulate adding funds to user wallet")
    public ResponseEntity<ApiResponse<WalletDto>> topUp(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        Long userId = getUserId(authentication);
        WalletDto wallet = walletService.topUp(userId, request.getAmount(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Wallet topped up successfully", wallet));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Get Wallet Transactions", description = "View credit/debit transaction statement")
    public ResponseEntity<ApiResponse<List<WalletTransactionDto>>> getTransactions(Authentication authentication) {
        Long userId = getUserId(authentication);
        List<WalletTransactionDto> txns = walletService.getTransactions(userId);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", txns));
    }

    @PostMapping("/debit")
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    @Operation(summary = "Debit Wallet (Internal)", description = "Internal endpoint for payment processing")
    public ResponseEntity<ApiResponse<WalletDto>> debit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WalletTransferRequest request) {
        WalletDto wallet = walletService.debit(request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Wallet debited successfully", wallet));
    }

    @PostMapping("/credit")
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    @Operation(summary = "Credit Wallet (Internal)", description = "Internal endpoint for refund or revenue credit")
    public ResponseEntity<ApiResponse<WalletDto>> credit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WalletTransferRequest request) {
        WalletDto wallet = walletService.credit(request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Wallet credited successfully", wallet));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    @Operation(summary = "Get Wallet by User ID (Internal)", description = "Internal endpoint to get user wallet")
    public ResponseEntity<ApiResponse<WalletDto>> getWalletByUserId(@PathVariable Long userId) {
        WalletDto wallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }
}
