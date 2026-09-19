package com.eshoppingzone.review.dto;

import com.eshoppingzone.review.entity.Review;
import com.eshoppingzone.review.enums.ReviewStatus;

import java.time.LocalDateTime;

public class ReviewDto {
    private Long id;
    private Long customerId;
    private String customerName;
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private Integer rating;
    private String title;
    private String comment;
    private ReviewStatus status;
    private Boolean verifiedPurchase;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReviewDto() {
    }

    public ReviewDto(Long id, Long customerId, String customerName, Long productId, Long orderId, Long orderItemId,
                     Integer rating, String title, String comment, ReviewStatus status, Boolean verifiedPurchase,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.productId = productId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.status = status;
        this.verifiedPurchase = verifiedPurchase;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ReviewDto fromEntity(Review review) {
        if (review == null) {
            return null;
        }
        return new ReviewDto(
                review.getId(),
                review.getCustomerId(),
                review.getCustomerName(),
                review.getProductId(),
                review.getOrderId(),
                review.getOrderItemId(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getStatus(),
                review.getVerifiedPurchase(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Boolean getVerifiedPurchase() {
        return verifiedPurchase;
    }

    public void setVerifiedPurchase(Boolean verifiedPurchase) {
        this.verifiedPurchase = verifiedPurchase;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
