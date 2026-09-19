package com.eshoppingzone.review.service;

import com.eshoppingzone.review.client.OrderClient;
import com.eshoppingzone.review.client.ProductClient;
import com.eshoppingzone.review.config.RabbitMQConfig;
import com.eshoppingzone.review.dto.*;
import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;
import com.eshoppingzone.review.exception.DuplicateReviewException;
import com.eshoppingzone.review.exception.InvalidReviewStateException;
import com.eshoppingzone.review.exception.ResourceNotFoundException;
import com.eshoppingzone.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

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

    // 1. Valid review creation
    @Test
    @DisplayName("1. Valid review creation succeeds with status PENDING")
    void testValidReviewCreation() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Great phone", "Loved the camera quality!");
        ReviewDto result = reviewService.createReview(50L, "John Doe", req);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(ReviewStatus.PENDING, result.getStatus());
        assertEquals(5, result.getRating());
        assertEquals(50L, result.getCustomerId());
        assertTrue(result.getVerifiedPurchase());
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.REVIEW_CREATED_ROUTING_KEY), any(ReviewEvent.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    // 2. Rating 1 accepted
    @Test
    @DisplayName("2. Rating 1 is accepted")
    void testRatingOneAccepted() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 1, "Poor", "Broke on day 1");
        ReviewDto result = reviewService.createReview(50L, "John", req);

        assertNotNull(result);
        assertEquals(1, result.getRating());
    }

    // 3. Rating 5 accepted
    @Test
    @DisplayName("3. Rating 5 is accepted")
    void testRatingFiveAccepted() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Awesome", "Super quality!");
        ReviewDto result = reviewService.createReview(50L, "John", req);

        assertNotNull(result);
        assertEquals(5, result.getRating());
    }

    // 4. Rating 0 rejected
    @Test
    @DisplayName("4. Rating 0 is rejected")
    void testRatingZeroRejected() {
        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 0, "Bad", "Comment");
        assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
    }

    // 5. Rating 6 rejected
    @Test
    @DisplayName("5. Rating 6 is rejected")
    void testRatingSixRejected() {
        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 6, "Invalid", "Comment");
        assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
    }

    // 6. Product not found
    @Test
    @DisplayName("6. Product not found throws ResourceNotFoundException")
    void testProductNotFound() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(999L)).thenReturn(ApiResponse.error("Not found"));

        CreateReviewRequest req = new CreateReviewRequest(999L, 10L, 100L, 5, "Title", "Valid comment text");
        assertThrows(ResourceNotFoundException.class, () -> reviewService.createReview(50L, "John", req));
    }

    // 7. Order not found
    @Test
    @DisplayName("7. Order not found throws ResourceNotFoundException")
    void testOrderNotFound() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(999L)).thenReturn(ApiResponse.error("Not found"));

        CreateReviewRequest req = new CreateReviewRequest(500L, 999L, 100L, 5, "Title", "Valid comment text");
        assertThrows(ResourceNotFoundException.class, () -> reviewService.createReview(50L, "John", req));
    }

    // 8. Order does not belong to customer
    @Test
    @DisplayName("8. Order does not belong to customer throws InvalidReviewStateException")
    void testOrderNotBelongingToCustomer() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        sampleOrder.setCustomerId(999L); // Different customer
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Title", "Valid comment text");
        InvalidReviewStateException ex = assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
        assertTrue(ex.getMessage().contains("does not belong to the authenticated customer"));
    }

    // 9. Order not DELIVERED
    @Test
    @DisplayName("9. Order not DELIVERED throws InvalidReviewStateException")
    void testOrderNotDelivered() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        sampleOrder.setStatus("PROCESSING");
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Title", "Valid comment text");
        InvalidReviewStateException ex = assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
        assertTrue(ex.getMessage().contains("DELIVERED orders"));
    }

    // 10. Product not present in order
    @Test
    @DisplayName("10. Product not present in order throws InvalidReviewStateException")
    void testProductNotPresentInOrder() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(777L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        // Request product 777, but orderItem 100 is for product 500
        CreateReviewRequest req = new CreateReviewRequest(777L, 10L, 100L, 5, "Title", "Valid comment text");
        InvalidReviewStateException ex = assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
        assertTrue(ex.getMessage().contains("does not match order item"));
    }

    // 11. OrderItem does not belong to order
    @Test
    @DisplayName("11. OrderItem does not belong to order throws InvalidReviewStateException")
    void testOrderItemNotBelongingToOrder() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 999L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 999L, 5, "Title", "Valid comment text");
        InvalidReviewStateException ex = assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
        assertTrue(ex.getMessage().contains("does not belong to order"));
    }

    // 12. Product ID does not match order item
    @Test
    @DisplayName("12. Product ID does not match order item throws InvalidReviewStateException")
    void testProductIdMismatchOrderItem() {
        sampleItem.setProductId(600L); // Item is for product 600
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Title", "Valid comment text");
        InvalidReviewStateException ex = assertThrows(InvalidReviewStateException.class, () -> reviewService.createReview(50L, "John", req));
        assertTrue(ex.getMessage().contains("does not match order item"));
    }

    // 13. Duplicate review rejected
    @Test
    @DisplayName("13. Duplicate review rejected via application check")
    void testDuplicateReviewRejected() {
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(true);

        CreateReviewRequest req = new CreateReviewRequest(500L, 10L, 100L, 5, "Title", "Valid comment text");
        assertThrows(DuplicateReviewException.class, () -> reviewService.createReview(50L, "John", req));
        verify(reviewRepository, never()).save(any());
    }

    // 14. Valid update
    @Test
    @DisplayName("14. Valid review update modifies rating, title, comment")
    void testValidReviewUpdate() {
        Review existing = new Review(1L, 50L, "John", 500L, 10L, 100L, 3, "Okay", "Decent", ReviewStatus.PENDING, true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        UpdateReviewRequest updateReq = new UpdateReviewRequest(4, "Better now", "Updated after use");
        ReviewDto result = reviewService.updateReview(1L, 50L, updateReq);

        assertNotNull(result);
        assertEquals(4, result.getRating());
        assertEquals("Better now", result.getTitle());
        assertEquals("Updated after use", result.getComment());
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.REVIEW_UPDATED_ROUTING_KEY), any(ReviewEvent.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    // 15. Unauthorized update
    @Test
    @DisplayName("15. Unauthorized review update throws AccessDeniedException")
    void testUnauthorizedReviewUpdate() {
        Review existing = new Review(1L, 50L, "John", 500L, 10L, 100L, 3, "Okay", "Decent", ReviewStatus.PENDING, true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateReviewRequest updateReq = new UpdateReviewRequest(4, "Title", "Comment text");
        assertThrows(AccessDeniedException.class, () -> reviewService.updateReview(1L, 999L, updateReq));
    }

    // 16. Approved review update becomes PENDING
    @Test
    @DisplayName("16. Approved review update resets status to PENDING for moderation")
    void testApprovedReviewUpdateBecomesPending() {
        Review existing = new Review(1L, 50L, "John", 500L, 10L, 100L, 5, "Super", "Loved it", ReviewStatus.APPROVED, true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        UpdateReviewRequest updateReq = new UpdateReviewRequest(4, "Edited", "Still good");
        ReviewDto result = reviewService.updateReview(1L, 50L, updateReq);

        assertEquals(ReviewStatus.PENDING, result.getStatus());
    }

    // 17. Soft delete
    @Test
    @DisplayName("17. Soft delete sets review status to HIDDEN")
    void testSoftDelete() {
        Review existing = new Review(1L, 50L, "John", 500L, 10L, 100L, 5, "Super", "Loved it", ReviewStatus.APPROVED, true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.deleteReview(1L, 50L, false);

        assertEquals(ReviewStatus.HIDDEN, existing.getStatus());
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.REVIEW_DELETED_ROUTING_KEY), any(ReviewEvent.class), any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    // 18. Rating calculation precision
    @Test
    @DisplayName("18. Rating calculation precision rounds to 2 decimal places")
    void testRatingCalculationPrecision() {
        Review r1 = new Review(1L, 1L, "U1", 500L, 10L, 1L, 5, "T1", "C1", ReviewStatus.APPROVED, true);
        Review r2 = new Review(2L, 2L, "U2", 500L, 11L, 2L, 4, "T2", "C2", ReviewStatus.APPROVED, true);
        Review r3 = new Review(3L, 3L, "U3", 500L, 12L, 3L, 5, "T3", "C3", ReviewStatus.APPROVED, true);

        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED)).thenReturn(List.of(r1, r2, r3));

        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(500L);

        // (5 + 4 + 5) / 3 = 14 / 3 = 4.67
        assertEquals(4.67, summary.getAverageRating());
        assertEquals(3L, summary.getTotalReviews());
    }

    // 19. Rating distribution
    @Test
    @DisplayName("19. Rating distribution returns exact counts for 1..5")
    void testRatingDistribution() {
        Review r1 = new Review(1L, 1L, "U1", 500L, 10L, 1L, 5, "T", "C", ReviewStatus.APPROVED, true);
        Review r2 = new Review(2L, 2L, "U2", 500L, 11L, 2L, 5, "T", "C", ReviewStatus.APPROVED, true);
        Review r3 = new Review(3L, 3L, "U3", 500L, 12L, 3L, 4, "T", "C", ReviewStatus.APPROVED, true);
        Review r4 = new Review(4L, 4L, "U4", 500L, 13L, 4L, 2, "T", "C", ReviewStatus.APPROVED, true);

        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED)).thenReturn(List.of(r1, r2, r3, r4));

        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(500L);

        assertEquals(2L, summary.getRatingDistribution().get("5"));
        assertEquals(1L, summary.getRatingDistribution().get("4"));
        assertEquals(0L, summary.getRatingDistribution().get("3"));
        assertEquals(1L, summary.getRatingDistribution().get("2"));
        assertEquals(0L, summary.getRatingDistribution().get("1"));
    }

    // 20. REJECTED excluded from rating
    @Test
    @DisplayName("20. REJECTED reviews are excluded from rating summary")
    void testRejectedExcludedFromRating() {
        Review r1 = new Review(1L, 1L, "U1", 500L, 10L, 1L, 5, "T", "C", ReviewStatus.APPROVED, true);
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED)).thenReturn(List.of(r1));

        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(500L);

        assertEquals(5.0, summary.getAverageRating());
        assertEquals(1L, summary.getTotalReviews());
        verify(reviewRepository).findByProductIdAndStatus(500L, ReviewStatus.APPROVED);
    }

    // 21. HIDDEN excluded from rating
    @Test
    @DisplayName("21. HIDDEN reviews are excluded from rating summary")
    void testHiddenExcludedFromRating() {
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED)).thenReturn(Collections.emptyList());

        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(500L);

        assertEquals(0.0, summary.getAverageRating());
        assertEquals(0L, summary.getTotalReviews());
    }

    // 22. APPROVED included in rating
    @Test
    @DisplayName("22. APPROVED reviews are included in rating summary")
    void testApprovedIncludedInRating() {
        Review r1 = new Review(1L, 1L, "U1", 500L, 10L, 1L, 4, "T", "C", ReviewStatus.APPROVED, true);
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED)).thenReturn(List.of(r1));

        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(500L);

        assertEquals(4.0, summary.getAverageRating());
        assertEquals(1L, summary.getTotalReviews());
    }

    // 23. Invalid state transition rejected
    @Test
    @DisplayName("23. Invalid state transition on already approved review throws InvalidReviewStateException")
    void testInvalidStateTransitionRejected() {
        Review r1 = new Review(1L, 1L, "U1", 500L, 10L, 1L, 4, "T", "C", ReviewStatus.APPROVED, true);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r1));

        assertThrows(InvalidReviewStateException.class, () -> reviewService.approveReview(1L));
        assertThrows(InvalidReviewStateException.class, () -> reviewService.rejectReview(1L, "Reason"));
    }
}
