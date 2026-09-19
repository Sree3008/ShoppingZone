package com.eshoppingzone.wallet.concurrency;

import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.entity.Wallet;
import com.eshoppingzone.wallet.exception.InsufficientBalanceException;
import com.eshoppingzone.wallet.repository.WalletRepository;
import com.eshoppingzone.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WalletConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:walletconcdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    private Long userId = 888L;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setBalance(new BigDecimal("1000.00"));
        walletRepository.save(w);
    }

    @Test
    @DisplayName("Two concurrent debit requests of 800 on balance of 1000: exactly 1 SUCCESS, 1 FAILURE, balance = 200")
    void testConcurrentWalletDebitRace() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    WalletTransferRequest req = new WalletTransferRequest(
                            userId,
                            new BigDecimal("800.00"),
                            "TXN-CONC-" + index,
                            "Concurrent debit " + index
                    );
                    walletService.debit(req);
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException ibe) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent debit threads timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one debit must succeed");
        assertEquals(1, failureCount.get(), "Exactly one debit must fail due to insufficient balance");

        // Verify database state directly
        Wallet finalWallet = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(finalWallet.getBalance()),
                "Final balance must be exactly 200.00");
        assertTrue(finalWallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Final balance must never be negative");
    }
}
