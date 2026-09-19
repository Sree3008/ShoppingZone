package com.eshoppingzone.inventory.concurrency;

import com.eshoppingzone.inventory.dto.StockReservationItem;
import com.eshoppingzone.inventory.dto.StockReservationRequest;
import com.eshoppingzone.inventory.entity.Inventory;
import com.eshoppingzone.inventory.exception.InsufficientStockException;
import com.eshoppingzone.inventory.repository.InventoryRepository;
import com.eshoppingzone.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:invconcdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1lc2hvcHBpbmctem9uZS1taWNyb3NlcnZpY2VzLXByb2plY3Qtc2VjdXJpdHk=");
    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long productId = 999L;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setAvailableStock(1);
        inv.setReservedStock(0);
        inventoryRepository.save(inv);
    }

    @Test
    @DisplayName("Two concurrent requests reserving stock=1 when available=1: exactly 1 SUCCESS, 1 FAILURE, stock >= 0")
    void testConcurrentStockReservationRace() throws Exception {
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
                    StockReservationRequest request = new StockReservationRequest(
                            "ORD-CONC-" + index,
                            List.of(new StockReservationItem(productId, 1))
                    );
                    inventoryService.reserveStock(request);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ise) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // Other concurrency exception
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent reservation threads timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one reservation request must succeed");
        assertEquals(1, failureCount.get(), "Exactly one reservation request must fail due to insufficient stock");

        // Verify database state directly
        Inventory finalInventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertEquals(0, finalInventory.getAvailableStock(), "Available stock must be exactly 0");
        assertEquals(1, finalInventory.getReservedStock(), "Reserved stock must be exactly 1");
        assertTrue(finalInventory.getAvailableStock() >= 0, "Available stock must never be negative");
    }
}
