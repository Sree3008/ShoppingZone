package com.eshoppingzone.wallet.service;

import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransactionDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.TransactionType;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.entity.WalletTransaction;
import com.eshoppingzone.wallet.exception.InsufficientBalanceException;
import com.eshoppingzone.wallet.exception.ResourceNotFoundException;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletIdempotencyService idempotencyService;
    private final com.eshoppingzone.wallet.audit.service.AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public WalletServiceImpl(WalletRepository walletRepository,
                             WalletTransactionRepository transactionRepository,
                             WalletIdempotencyService idempotencyService,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.eshoppingzone.wallet.audit.service.AuditLogService auditLogService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    public WalletServiceImpl(WalletRepository walletRepository,
                             WalletTransactionRepository transactionRepository,
                             WalletIdempotencyService idempotencyService) {
        this(walletRepository, transactionRepository, idempotencyService, null);
    }

    public WalletServiceImpl(WalletRepository walletRepository, WalletTransactionRepository transactionRepository) {
        this(walletRepository, transactionRepository, null, null);
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

    private Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Wallet w = new Wallet();
                    w.setUserId(userId);
                    w.setBalance(BigDecimal.ZERO);
                    Wallet saved = walletRepository.save(w);
                    audit("WALLET_CREATED", "WALLET", String.valueOf(saved.getId()), "SUCCESS", null,
                            safeMeta("userId", userId, "initialBalance", BigDecimal.ZERO));
                    return saved;
                });
    }

    private Wallet getOrCreateWalletWithLock(Long userId) {
        return walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    try {
                        Wallet w = new Wallet();
                        w.setUserId(userId);
                        w.setBalance(BigDecimal.ZERO);
                        Wallet saved = walletRepository.save(w);
                        audit("WALLET_CREATED", "WALLET", String.valueOf(saved.getId()), "SUCCESS", null,
                                safeMeta("userId", userId, "initialBalance", BigDecimal.ZERO));
                        return saved;
                    } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                        return walletRepository.findByUserIdWithLock(userId).orElseThrow();
                    }
                });
    }

    @Override
    @Transactional
    public WalletDto getWalletByUserId(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return WalletDto.fromEntity(wallet);
    }

    @Override
    @Transactional
    public BigDecimal getBalance(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return wallet.getBalance();
    }

    @Override
    @Transactional
    public WalletDto topUp(Long userId, BigDecimal amount) {
        return topUp(userId, amount, null);
    }

    @Override
    @Transactional
    public WalletDto topUp(Long userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            String fingerprint = idempotencyService.computeTopUpFingerprint(userId, amount);
            Optional<com.eshoppingzone.wallet.entity.WalletIdempotencyRecord> recordOpt =
                    idempotencyService.checkOrStartProcessing(idempotencyKey, "WALLET_TOPUP", userId, fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == com.eshoppingzone.wallet.entity.IdempotencyStatus.COMPLETED) {
                Optional<WalletDto> cached = idempotencyService.parseResponseBody(recordOpt.get(), WalletDto.class);
                if (cached.isPresent()) {
                    log.info("Returning cached idempotent top-up response for key: {}", idempotencyKey);
                    audit("WALLET_TOPUP_REPLAY", "WALLET", String.valueOf(userId), "SUCCESS", null,
                            safeMeta("userId", userId, "amount", amount, "idempotencyKey", idempotencyKey));
                    return cached.get();
                }
            }
        }

        try {
            Wallet wallet = getOrCreateWalletWithLock(userId);
            wallet.setBalance(wallet.getBalance().add(amount));
            Wallet saved = walletRepository.save(wallet);

            String txnRef = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? ("TOPUP-" + idempotencyKey)
                    : ("TOPUP-" + System.currentTimeMillis());
            recordTransaction(saved.getId(), TransactionType.TOP_UP, amount, txnRef, "Simulated wallet top-up");
            log.info("Top-up successful for userId: {}, amount: {}, new balance: {}", userId, amount, saved.getBalance());
            audit("WALLET_TOPUP", "WALLET", String.valueOf(saved.getId()), "SUCCESS", null,
                    safeMeta("userId", userId, "amount", amount, "newBalance", saved.getBalance(), "reference", txnRef));

            WalletDto result = WalletDto.fromEntity(saved);
            if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.markCompleted(idempotencyKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()
                    && !(e instanceof com.eshoppingzone.wallet.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(idempotencyKey, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public WalletDto debit(WalletTransferRequest request) {
        return debit(request, null);
    }

    @Override
    @Transactional
    public WalletDto debit(WalletTransferRequest request, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : request.getReference();

        if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
            String fingerprint = idempotencyService.computeTransferFingerprint(request);
            Optional<com.eshoppingzone.wallet.entity.WalletIdempotencyRecord> recordOpt =
                    idempotencyService.checkOrStartProcessing(effectiveKey, "WALLET_DEBIT", request.getUserId(), fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == com.eshoppingzone.wallet.entity.IdempotencyStatus.COMPLETED) {
                Optional<WalletDto> cached = idempotencyService.parseResponseBody(recordOpt.get(), WalletDto.class);
                if (cached.isPresent()) {
                    log.info("Returning cached idempotent debit response for key: {}", effectiveKey);
                    audit("WALLET_DEBIT_REPLAY", "WALLET", String.valueOf(request.getUserId()), "SUCCESS", null,
                            safeMeta("userId", request.getUserId(), "amount", request.getAmount(), "idempotencyKey", effectiveKey));
                    return cached.get();
                }
                Wallet currentWallet = getOrCreateWallet(request.getUserId());
                return WalletDto.fromEntity(currentWallet);
            }
        }

        try {
            Wallet wallet = getOrCreateWalletWithLock(request.getUserId());

            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                audit("WALLET_TRANSACTION_FAILED", "WALLET", String.valueOf(wallet.getId()), "FAILURE", "Insufficient balance",
                        safeMeta("userId", request.getUserId(), "requestedAmount", request.getAmount(), "currentBalance", wallet.getBalance()));
                throw new InsufficientBalanceException("Insufficient wallet balance. Current balance: " + wallet.getBalance() + ", Requested: " + request.getAmount());
            }

            wallet.setBalance(wallet.getBalance().subtract(request.getAmount()));
            Wallet saved = walletRepository.save(wallet);

            recordTransaction(saved.getId(), TransactionType.DEBIT, request.getAmount(), request.getReference(), request.getDescription());
            log.info("Wallet debited for userId: {}, amount: {}, new balance: {}", request.getUserId(), request.getAmount(), saved.getBalance());
            audit("WALLET_DEBIT", "WALLET", String.valueOf(saved.getId()), "SUCCESS", null,
                    safeMeta("userId", request.getUserId(), "amount", request.getAmount(), "newBalance", saved.getBalance(), "reference", request.getReference(), "description", request.getDescription()));

            WalletDto result = WalletDto.fromEntity(saved);
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
                idempotencyService.markCompleted(effectiveKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()
                    && !(e instanceof com.eshoppingzone.wallet.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(effectiveKey, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public WalletDto credit(WalletTransferRequest request) {
        return credit(request, null);
    }

    @Override
    @Transactional
    public WalletDto credit(WalletTransferRequest request, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : request.getReference();

        if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
            String fingerprint = idempotencyService.computeTransferFingerprint(request);
            Optional<com.eshoppingzone.wallet.entity.WalletIdempotencyRecord> recordOpt =
                    idempotencyService.checkOrStartProcessing(effectiveKey, "WALLET_CREDIT", request.getUserId(), fingerprint);
            if (recordOpt.isPresent() && recordOpt.get().getStatus() == com.eshoppingzone.wallet.entity.IdempotencyStatus.COMPLETED) {
                Optional<WalletDto> cached = idempotencyService.parseResponseBody(recordOpt.get(), WalletDto.class);
                if (cached.isPresent()) {
                    log.info("Returning cached idempotent credit response for key: {}", effectiveKey);
                    audit("WALLET_CREDIT_REPLAY", "WALLET", String.valueOf(request.getUserId()), "SUCCESS", null,
                            safeMeta("userId", request.getUserId(), "amount", request.getAmount(), "idempotencyKey", effectiveKey));
                    return cached.get();
                }
                Wallet currentWallet = getOrCreateWallet(request.getUserId());
                return WalletDto.fromEntity(currentWallet);
            }
        }

        try {
            Wallet wallet = getOrCreateWalletWithLock(request.getUserId());
            wallet.setBalance(wallet.getBalance().add(request.getAmount()));
            Wallet saved = walletRepository.save(wallet);

            TransactionType type = (request.getDescription() != null && request.getDescription().toLowerCase().contains("refund")) ?
                    TransactionType.REFUND : TransactionType.CREDIT;

            recordTransaction(saved.getId(), type, request.getAmount(), request.getReference(), request.getDescription());
            log.info("Wallet credited for userId: {}, amount: {}, new balance: {}", request.getUserId(), request.getAmount(), saved.getBalance());
            String actionName = (type == TransactionType.REFUND) ? "REFUND_CREDIT" : "WALLET_CREDIT";
            audit(actionName, "WALLET", String.valueOf(saved.getId()), "SUCCESS", null,
                    safeMeta("userId", request.getUserId(), "amount", request.getAmount(), "newBalance", saved.getBalance(), "reference", request.getReference(), "description", request.getDescription()));

            WalletDto result = WalletDto.fromEntity(saved);
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()) {
                idempotencyService.markCompleted(effectiveKey, result);
            }
            return result;
        } catch (Exception e) {
            if (idempotencyService != null && effectiveKey != null && !effectiveKey.isBlank()
                    && !(e instanceof com.eshoppingzone.wallet.exception.IdempotencyConflictException)) {
                idempotencyService.markFailed(effectiveKey, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletTransactionDto> getTransactions(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for userId: " + userId));

        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(WalletTransactionDto::fromEntity)
                .collect(Collectors.toList());
    }

    private void recordTransaction(Long walletId, TransactionType type, BigDecimal amount, String reference, String description) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWalletId(walletId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setReference(reference);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }
}
