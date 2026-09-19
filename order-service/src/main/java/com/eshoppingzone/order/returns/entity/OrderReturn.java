package com.eshoppingzone.order.returns.entity;

import com.eshoppingzone.order.returns.enums.RefundStatus;
import com.eshoppingzone.order.returns.enums.RestockStatus;
import com.eshoppingzone.order.returns.enums.ReturnReason;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_returns", indexes = {
        @Index(name = "idx_order_returns_number", columnList = "returnNumber", unique = true),
        @Index(name = "idx_order_returns_order_id", columnList = "orderId"),
        @Index(name = "idx_order_returns_customer_id", columnList = "customerId"),
        @Index(name = "idx_order_returns_item_id", columnList = "orderItemId"),
        @Index(name = "idx_order_returns_idempotency", columnList = "idempotencyKey", unique = true)
})
public class OrderReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String returnNumber;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 50)
    private String orderNumber;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason returnReason;

    @Column(length = 1000)
    private String returnDescription;

    @Column(length = 1000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnStatus status = ReturnStatus.RETURN_REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus refundStatus = RefundStatus.NOT_INITIATED;

    private Long refundId;

    @Column(length = 100)
    private String refundReference;

    @Column(length = 1000)
    private String refundFailureReason;

    @Column(nullable = false)
    private boolean inventoryRestocked = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RestockStatus inventoryRestockStatus = RestockStatus.NOT_APPLICABLE;

    @Column(length = 1000)
    private String inventoryRestockFailureReason;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public OrderReturn() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = ReturnStatus.RETURN_REQUESTED;
        }
        if (this.refundStatus == null) {
            this.refundStatus = RefundStatus.NOT_INITIATED;
        }
        if (this.inventoryRestockStatus == null) {
            this.inventoryRestockStatus = RestockStatus.NOT_APPLICABLE;
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

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public ReturnReason getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(ReturnReason returnReason) {
        this.returnReason = returnReason;
    }

    public String getReturnDescription() {
        return returnDescription;
    }

    public void setReturnDescription(String returnDescription) {
        this.returnDescription = returnDescription;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public void setStatus(ReturnStatus status) {
        this.status = status;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(RefundStatus refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Long getRefundId() {
        return refundId;
    }

    public void setRefundId(Long refundId) {
        this.refundId = refundId;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public String getRefundFailureReason() {
        return refundFailureReason;
    }

    public void setRefundFailureReason(String refundFailureReason) {
        this.refundFailureReason = refundFailureReason;
    }

    public boolean isInventoryRestocked() {
        return inventoryRestocked;
    }

    public void setInventoryRestocked(boolean inventoryRestocked) {
        this.inventoryRestocked = inventoryRestocked;
    }

    public RestockStatus getInventoryRestockStatus() {
        return inventoryRestockStatus;
    }

    public void setInventoryRestockStatus(RestockStatus inventoryRestockStatus) {
        this.inventoryRestockStatus = inventoryRestockStatus;
    }

    public String getInventoryRestockFailureReason() {
        return inventoryRestockFailureReason;
    }

    public void setInventoryRestockFailureReason(String inventoryRestockFailureReason) {
        this.inventoryRestockFailureReason = inventoryRestockFailureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
