package com.eshoppingzone.review.controller;

import com.eshoppingzone.review.dto.ApiResponse;
import com.eshoppingzone.review.dto.ReviewDto;
import com.eshoppingzone.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Review Moderation", description = "Admin endpoints for moderating and managing product reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @Operation(summary = "Get All Reviews", description = "Retrieve paginated list of all reviews across all statuses")
    public ResponseEntity<ApiResponse<Page<ReviewDto>>> getAllReviews(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ReviewDto> reviews = reviewService.getAllReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully", reviews));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get Pending Reviews", description = "Retrieve paginated list of reviews awaiting moderation")
    public ResponseEntity<ApiResponse<Page<ReviewDto>>> getPendingReviews(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ReviewDto> reviews = reviewService.getPendingReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success("Pending reviews retrieved successfully", reviews));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Review by ID", description = "Admin review detail lookup")
    public ResponseEntity<ApiResponse<ReviewDto>> getReviewById(@PathVariable Long id) {
        ReviewDto review = reviewService.getReviewById(id, null, true);
        return ResponseEntity.ok(ApiResponse.success("Review retrieved successfully", review));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve Review", description = "Admin approves a pending review making it publicly visible")
    public ResponseEntity<ApiResponse<ReviewDto>> approveReview(@PathVariable Long id) {
        ReviewDto review = reviewService.approveReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review approved successfully", review));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject Review", description = "Admin rejects a pending review")
    public ResponseEntity<ApiResponse<ReviewDto>> rejectReview(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Violates review guidelines") String reason) {
        ReviewDto review = reviewService.rejectReview(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Review rejected successfully", review));
    }

    @PutMapping("/{id}/hide")
    @Operation(summary = "Hide Review", description = "Admin hides a review, removing it from public visibility and ratings")
    public ResponseEntity<ApiResponse<ReviewDto>> hideReview(@PathVariable Long id) {
        ReviewDto review = reviewService.hideReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review hidden successfully", review));
    }
}
