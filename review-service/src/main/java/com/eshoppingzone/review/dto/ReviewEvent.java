package com.eshoppingzone.review.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReviewEvent implements Serializable {
    private String eventId;
    private String eventType;
    private Long reviewId;
    private Long productId;
    private Long orderId;
    private Long customerId;
    private Integer rating;
    private LocalDateTime timestamp;

    public ReviewEvent() {
    }

    public ReviewEvent(String eventType, Long reviewId, Long productId, Long orderId, Long customerId, Integer rating) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.reviewId = reviewId;
        this.productId = productId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.rating = rating;
        this.timestamp = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
