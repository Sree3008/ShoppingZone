package com.eshoppingzone.review.audit;

import com.eshoppingzone.review.audit.entity.AuditLog;
import com.eshoppingzone.review.audit.service.AuditLogService;
import com.eshoppingzone.review.client.OrderClient;
import com.eshoppingzone.review.client.ProductClient;
import com.eshoppingzone.review.dto.ApiResponse;
import com.eshoppingzone.review.dto.CreateReviewRequest;
import com.eshoppingzone.review.dto.OrderDto;
import com.eshoppingzone.review.dto.OrderItemDto;
import com.eshoppingzone.review.dto.ProductDto;
import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;
import com.eshoppingzone.review.repository.ReviewRepository;
import com.eshoppingzone.review.service.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReviewAuditTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AuditLogService auditLogService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(
                reviewRepository,
                orderClient,
                productClient,
                rabbitTemplate,
                auditLogService
        );
    }

    @Test
    void testCreateReviewEmitsAuditLog() {
        CreateReviewRequest request = new CreateReviewRequest(500L, 10L, 100L, 5, "Great product", "Love it!");

        ProductDto productDto = new ProductDto(500L, 1L, "Phone", "Desc", "Electronics", new BigDecimal("100.00"), "img.jpg", "ACTIVE");
        when(productClient.getProductInternal(500L)).thenReturn(new ApiResponse<>(true, "OK", productDto));

        OrderItemDto itemDto = new OrderItemDto(100L, 500L, "Phone", new BigDecimal("100.00"), 1, new BigDecimal("100.00"));
        OrderDto orderDto = new OrderDto(10L, "ORD-123", 50L, "DELIVERED", new ArrayList<>(List.of(itemDto)));
        when(orderClient.getOrderInternal(10L)).thenReturn(new ApiResponse<>(true, "OK", orderDto));

        when(reviewRepository.existsByCustomerIdAndOrderItemId(50L, 100L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        reviewService.createReview(50L, "John", request);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("REVIEW"),
                eq("1"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("REVIEW_CREATED", actionCaptor.getValue());
    }

    @Test
    void testApproveReviewEmitsAuditLog() {
        Review review = new Review();
        review.setId(2L);
        review.setProductId(500L);
        review.setRating(5);
        review.setStatus(ReviewStatus.PENDING);

        when(reviewRepository.findById(2L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenReturn(review);

        reviewService.approveReview(2L);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logAction(
                actionCaptor.capture(),
                eq("REVIEW"),
                eq("2"),
                eq("SUCCESS"),
                isNull(),
                any()
        );

        assertEquals("REVIEW_APPROVED", actionCaptor.getValue());
    }

    @Test
    void testAuditLogImmutability() {
        AuditLog log = new AuditLog();
        assertThrows(UnsupportedOperationException.class, log::preUpdate);
        assertThrows(UnsupportedOperationException.class, log::preRemove);
    }
}
