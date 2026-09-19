package com.eshoppingzone.review.concurrency;

import com.eshoppingzone.review.client.OrderClient;
import com.eshoppingzone.review.client.ProductClient;
import com.eshoppingzone.review.dto.*;
import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.exception.DuplicateReviewException;
import com.eshoppingzone.review.repository.ReviewRepository;
import com.eshoppingzone.review.service.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateReviewTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private OrderDto sampleOrder;
    private OrderItemDto sampleItem;
    private ProductDto sampleProduct;

    @BeforeEach
    void setUp() {
        sampleItem = new OrderItemDto(100L, 500L, "Test Smartphone", new BigDecimal("299.99"), 1, new BigDecimal("299.99"));
        sampleOrder = new OrderDto(10L, "ORD-12345", 50L, "DELIVERED", new ArrayList<>(List.of(sampleItem)));
        sampleProduct = new ProductDto(500L, 1L, "Test Smartphone", "Description", "Electronics", new BigDecimal("299.99"), "image.jpg", "ACTIVE");
    }

    @Test
    @DisplayName("Sequential: first review succeeds, second review rejected with DuplicateReviewException (409)")
    void testSequentialDuplicateReview() {
        // First call: not existing yet
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Great", "First review submission");
        ReviewDto firstResult = reviewService.createReview(50L, "John", req);
        assertNotNull(firstResult);

        // Second call: existing check returns true
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(true);

        CreateReviewRequest secondReq = new CreateReviewRequest(500L, 10L, 100L, 4, "Updated thoughts", "Second duplicate submission");
        assertThrows(DuplicateReviewException.class, () -> reviewService.createReview(50L, "John", secondReq));
    }

    @Test
    @DisplayName("Concurrent: database unique constraint catches race condition, exactly one succeeds and one throws DuplicateReviewException")
    void testConcurrentDuplicateReviewWithDatabaseUniqueConstraint() throws Exception {
        // Both threads pass in-memory check (simulating concurrent execution before either commits)
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        AtomicInteger saveCount = new AtomicInteger(0);
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            int count = saveCount.incrementAndGet();
            if (count == 1) {
                Review r = i.getArgument(0);
                r.setId(1L);
                return r;
            } else {
                // Second concurrent thread hits DB unique constraint uk_customer_order_item_review
                throw new DataIntegrityViolationException("Duplicate entry for key uk_customer_order_item_review");
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<ReviewDto>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Great", "Concurrent submission");
                return reviewService.createReview(50L, "John", req);
            }));
        }

        int successCount = 0;
        int conflictCount = 0;

        for (Future<ReviewDto> future : futures) {
            try {
                ReviewDto dto = future.get();
                if (dto != null) {
                    successCount++;
                }
            } catch (ExecutionException ee) {
                if (ee.getCause() instanceof DuplicateReviewException) {
                    conflictCount++;
                }
            }
        }

        executor.shutdown();

        assertEquals(1, successCount, "Exactly one concurrent review creation should succeed");
        assertEquals(1, conflictCount, "Exactly one concurrent review creation should receive DuplicateReviewException");
    }
}
