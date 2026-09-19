package com.eshoppingzone.cart.concurrency;

import com.eshoppingzone.cart.client.ProductClient;
import com.eshoppingzone.cart.dto.AddToCartRequest;
import com.eshoppingzone.cart.dto.ApiResponse;
import com.eshoppingzone.cart.dto.ProductSnapshotDto;
import com.eshoppingzone.cart.entity.Cart;
import com.eshoppingzone.cart.repository.CartItemRepository;
import com.eshoppingzone.cart.repository.CartRepository;
import com.eshoppingzone.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
class CartConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:cartconcdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1lc2hvcHBpbmctem9uZS1taWNyb3NlcnZpY2VzLXByb2plY3Qtc2VjdXJpdHk=");
    }

    @MockBean
    private ProductClient productClient;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Long customerId = 66L;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        ProductSnapshotDto product = new ProductSnapshotDto();
        product.setId(10L);
        product.setName("Concurrent Test Product");
        product.setPrice(new BigDecimal("50.00"));
        product.setStatus("ACTIVE");
        product.setMerchantId(2L);
        when(productClient.getProductById(anyLong())).thenReturn(ApiResponse.success("Product found", product));
    }

    @Test
    @DisplayName("Concurrent add-to-cart operations maintain consistency")
    void testConcurrentAddToCart() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    AddToCartRequest req = new AddToCartRequest();
                    req.setProductId(10L);
                    req.setQuantity(1);
                    cartService.addToCart(customerId, req);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    // Handled / conflict
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent add to cart threads timed out");
        executor.shutdown();

        assertTrue(completedCount.get() >= 1, "At least one add-to-cart operation must complete successfully");

        Cart cart = cartRepository.findByCustomerId(customerId).orElseThrow();
        assertNotNull(cart.getVersion(), "Cart entity must have a valid version");
        assertFalse(cart.getItems().isEmpty(), "Cart items must not be empty");
    }
}
