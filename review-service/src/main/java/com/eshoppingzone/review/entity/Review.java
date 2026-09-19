package com.eshoppingzone.review.entity;

import com.eshoppingzone.review.enums.ReviewStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_order_item_review", columnNames = {"customer_id", "order_item_id"})
        },
        indexes = {
                @Index(name = "idx_reviews_product_id", columnList = "product_id"),
                @Index(name = "idx_reviews_customer_id", columnList = "customer_id"),
                @Index(name = "idx_reviews_order_id", columnList = "order_id"),
                @Index(name = "idx_reviews_order_item_id", columnList = "order_item_id"),
                @Index(name = "idx_reviews_status", columnList = "status"),
                @Index(name = "idx_reviews_product_status", columnList = "product_id, status")
        }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 150)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase = true;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Review() {
    }

    public Review(Long id, Long customerId, String customerName, Long productId, Long orderId, Long orderItemId,
                  Integer rating, String title, String comment, ReviewStatus status, Boolean verifiedPurchase) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.productId = productId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.status = status != null ? status : ReviewStatus.PENDING;
        this.verifiedPurchase = verifiedPurchase != null ? verifiedPurchase : true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReviewStatus.PENDING;
        }
        if (this.verifiedPurchase == null) {
            this.verifiedPurchase = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
