package com.eshoppingzone.review.controller;

import com.eshoppingzone.review.dto.ApiResponse;
import com.eshoppingzone.review.dto.CreateReviewRequest;
import com.eshoppingzone.review.dto.ProductRatingSummaryDto;
import com.eshoppingzone.review.dto.ReviewDto;
import com.eshoppingzone.review.dto.UpdateReviewRequest;
import com.eshoppingzone.review.security.UserPrincipal;
import com.eshoppingzone.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Review Management", description = "Customer and public endpoints for product reviews and ratings")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Submit Product Review", description = "Submit a review for a delivered purchased product")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            Authentication authentication,
            @Valid @RequestBody CreateReviewRequest request) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ReviewDto review = reviewService.createReview(principal.getUserId(), principal.getUsername(), request);
        return new ResponseEntity<>(ApiResponse.success("Review submitted successfully", review), HttpStatus.CREATED);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get Product Reviews", description = "Public paginated listing of approved reviews for a product")
    public ResponseEntity<ApiResponse<Page<ReviewDto>>> getProductReviews(
            @PathVariable Long productId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ReviewDto> reviews = reviewService.getProductReviews(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Product reviews retrieved successfully", reviews));
    }

    @GetMapping("/product/{productId}/summary")
    @Operation(summary = "Get Product Rating Summary", description = "Public rating summary and distribution for a product")
    public ResponseEntity<ApiResponse<ProductRatingSummaryDto>> getProductRatingSummary(
            @PathVariable Long productId) {
        ProductRatingSummaryDto summary = reviewService.getProductRatingSummary(productId);
        return ResponseEntity.ok(ApiResponse.success("Product rating summary retrieved successfully", summary));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Review by ID", description = "Retrieve review details by review ID")
    public ResponseEntity<ApiResponse<ReviewDto>> getReviewById(
            @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = null;
        boolean isAdmin = false;
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            requesterId = principal.getUserId();
            isAdmin = "ADMIN".equalsIgnoreCase(principal.getRole());
        }
        ReviewDto review = reviewService.getReviewById(id, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Review retrieved successfully", review));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get My Reviews", description = "Retrieve all reviews submitted by the authenticated customer")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getMyReviews(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<ReviewDto> reviews = reviewService.getMyReviews(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Customer reviews retrieved successfully", reviews));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update Review", description = "Update rating, title, and comment for customer's own review")
    public ResponseEntity<ApiResponse<ReviewDto>> updateReview(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateReviewRequest request) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ReviewDto updated = reviewService.updateReview(id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Delete Review", description = "Soft delete customer's own review")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(principal.getRole());
        reviewService.deleteReview(id, principal.getUserId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }
}
