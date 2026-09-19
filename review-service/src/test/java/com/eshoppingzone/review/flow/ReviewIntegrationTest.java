package com.eshoppingzone.review.flow;

import com.eshoppingzone.review.client.OrderClient;
import com.eshoppingzone.review.client.ProductClient;
import com.eshoppingzone.review.dto.*;
import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewIntegrationTest {

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
    private Review statefulReview;

    @BeforeEach
    void setUp() {
        sampleItem = new OrderItemDto(100L, 500L, "Test Smartphone", new BigDecimal("299.99"), 1, new BigDecimal("299.99"));
        sampleOrder = new OrderDto(10L, "ORD-12345", 50L, "DELIVERED", new ArrayList<>(List.of(sampleItem)));
        sampleProduct = new ProductDto(500L, 1L, "Test Smartphone", "Description", "Electronics", new BigDecimal("299.99"), "image.jpg", "ACTIVE");
    }

    @Test
    @DisplayName("Complete Review Lifecycle Flow: Create (PENDING) -> Excluded from public -> Admin Approve (APPROVED) -> Visible & in Summary -> Edit (PENDING) -> Delete (HIDDEN)")
    void testCompleteReviewLifecycleFlow() {
        // Step 1: Customer creates review for delivered order
        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(productClient.getProductInternal(500L)).thenReturn(ApiResponse.success(sampleProduct));
        when(orderClient.getOrderInternal(10L)).thenReturn(ApiResponse.success(sampleOrder));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setId(1L);
            statefulReview = r;
            return r;
        });

        CreateReviewRequest createReq = new CreateReviewRequest(500L, 10L, 100L, 5, "Initial Title", "Initial excellent comment");
        ReviewDto created = reviewService.createReview(50L, "John", createReq);

        assertNotNull(created);
        assertEquals(ReviewStatus.PENDING, created.getStatus());
        assertEquals(5, created.getRating());

        // Step 2: While PENDING, public product review listing returns nothing
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(500L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        Page<ReviewDto> publicReviewsWhilePending = reviewService.getProductReviews(500L, PageRequest.of(0, 10));
        assertTrue(publicReviewsWhilePending.isEmpty(), "Pending reviews must not appear in public listing");

        // Step 3: While PENDING, product rating summary has 0 reviews
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED))
                .thenReturn(Collections.emptyList());
        ProductRatingSummaryDto summaryWhilePending = reviewService.getProductRatingSummary(500L);
        assertEquals(0L, summaryWhilePending.getTotalReviews());
        assertEquals(0.0, summaryWhilePending.getAverageRating());

        // Step 4: Admin approves the review
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(statefulReview));
        ReviewDto approved = reviewService.approveReview(1L);
        assertEquals(ReviewStatus.APPROVED, approved.getStatus());

        // Step 5: After approval, review appears in public reviews
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(500L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(statefulReview)));
        Page<ReviewDto> publicReviewsApproved = reviewService.getProductReviews(500L, PageRequest.of(0, 10));
        assertEquals(1, publicReviewsApproved.getTotalElements());

        // Step 6: Rating summary now includes the approved review
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED))
                .thenReturn(List.of(statefulReview));
        ProductRatingSummaryDto summaryApproved = reviewService.getProductRatingSummary(500L);
        assertEquals(1L, summaryApproved.getTotalReviews());
        assertEquals(5.0, summaryApproved.getAverageRating());
        assertEquals(1L, summaryApproved.getRatingDistribution().get("5"));

        // Step 7: Customer edits approved review -> status resets to PENDING
        UpdateReviewRequest updateReq = new UpdateReviewRequest(4, "Updated Title", "Updated comment after 1 month");
        ReviewDto edited = reviewService.updateReview(1L, 50L, updateReq);
        assertEquals(ReviewStatus.PENDING, edited.getStatus());
        assertEquals(4, edited.getRating());

        // Step 8: Since status reset to PENDING, it is excluded from public reviews and rating summary again
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(500L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        Page<ReviewDto> publicReviewsAfterEdit = reviewService.getProductReviews(500L, PageRequest.of(0, 10));
        assertTrue(publicReviewsAfterEdit.isEmpty());

        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED))
                .thenReturn(Collections.emptyList());
        ProductRatingSummaryDto summaryAfterEdit = reviewService.getProductRatingSummary(500L);
        assertEquals(0L, summaryAfterEdit.getTotalReviews());

        // Step 9: Admin re-approves edited review
        ReviewDto reApproved = reviewService.approveReview(1L);
        assertEquals(ReviewStatus.APPROVED, reApproved.getStatus());

        // Step 10: Rating summary reflects updated rating (4 stars)
        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED))
                .thenReturn(List.of(statefulReview));
        ProductRatingSummaryDto summaryReApproved = reviewService.getProductRatingSummary(500L);
        assertEquals(1L, summaryReApproved.getTotalReviews());
        assertEquals(4.0, summaryReApproved.getAverageRating());
        assertEquals(1L, summaryReApproved.getRatingDistribution().get("4"));
        assertEquals(0L, summaryReApproved.getRatingDistribution().get("5"));

        // Step 11: Customer deletes review -> soft deletion sets status to HIDDEN
        reviewService.deleteReview(1L, 50L, false);
        assertEquals(ReviewStatus.HIDDEN, statefulReview.getStatus());

        // Step 12: Review is completely excluded from public listings and rating summary
        when(reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(eq(500L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        Page<ReviewDto> publicReviewsAfterDelete = reviewService.getProductReviews(500L, PageRequest.of(0, 10));
        assertTrue(publicReviewsAfterDelete.isEmpty());

        when(reviewRepository.findByProductIdAndStatus(500L, ReviewStatus.APPROVED))
                .thenReturn(Collections.emptyList());
        ProductRatingSummaryDto summaryAfterDelete = reviewService.getProductRatingSummary(500L);
        assertEquals(0L, summaryAfterDelete.getTotalReviews());
    }
}
