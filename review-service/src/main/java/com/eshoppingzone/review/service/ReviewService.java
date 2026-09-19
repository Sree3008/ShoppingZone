package com.eshoppingzone.review.service;

import com.eshoppingzone.review.dto.CreateReviewRequest;
import com.eshoppingzone.review.dto.ProductRatingSummaryDto;
import com.eshoppingzone.review.dto.ReviewDto;
import com.eshoppingzone.review.dto.UpdateReviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReviewService {

    ReviewDto createReview(Long customerId, String customerName, CreateReviewRequest request);

    Page<ReviewDto> getProductReviews(Long productId, Pageable pageable);

    ProductRatingSummaryDto getProductRatingSummary(Long productId);

    ReviewDto getReviewById(Long id, Long requesterId, boolean isAdmin);

    List<ReviewDto> getMyReviews(Long customerId);

    ReviewDto updateReview(Long id, Long customerId, UpdateReviewRequest request);

    void deleteReview(Long id, Long customerId, boolean isAdmin);

    Page<ReviewDto> getAllReviews(Pageable pageable);

    Page<ReviewDto> getPendingReviews(Pageable pageable);

    ReviewDto approveReview(Long id);

    ReviewDto rejectReview(Long id, String reason);

    ReviewDto hideReview(Long id);
}
