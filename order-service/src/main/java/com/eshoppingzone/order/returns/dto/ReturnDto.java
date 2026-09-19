package com.eshoppingzone.order.returns.dto;

import com.eshoppingzone.order.returns.entity.OrderReturn;
import com.eshoppingzone.order.returns.enums.RefundStatus;
import com.eshoppingzone.order.returns.enums.RestockStatus;
import com.eshoppingzone.order.returns.enums.ReturnReason;
import com.eshoppingzone.order.returns.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReturnDto {

    private Long id;
    private String returnNumber;
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal refundAmount;
    private ReturnReason returnReason;
    private String returnDescription;
    private String rejectionReason;
    private ReturnStatus status;
    private RefundStatus refundStatus;
    private Long refundId;
    private String refundReference;
    private String refundFailureReason;
    private boolean inventoryRestocked;
    private RestockStatus inventoryRestockStatus;
    private String inventoryRestockFailureReason;
    private String idempotencyKey;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReturnDto() {
    }

    public static ReturnDto fromEntity(OrderReturn entity) {
        if (entity == null) return null;
        ReturnDto dto = new ReturnDto();
        dto.setId(entity.getId());
        dto.setReturnNumber(entity.getReturnNumber());
        dto.setOrderId(entity.getOrderId());
        dto.setOrderNumber(entity.getOrderNumber());
        dto.setCustomerId(entity.getCustomerId());
        dto.setOrderItemId(entity.getOrderItemId());
        dto.setProductId(entity.getProductId());
        dto.setProductName(entity.getProductName());
        dto.setQuantity(entity.getQuantity());
        dto.setRefundAmount(entity.getRefundAmount());
        dto.setReturnReason(entity.getReturnReason());
        dto.setReturnDescription(entity.getReturnDescription());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setStatus(entity.getStatus());
        dto.setRefundStatus(entity.getRefundStatus());
        dto.setRefundId(entity.getRefundId());
        dto.setRefundReference(entity.getRefundReference());
        dto.setRefundFailureReason(entity.getRefundFailureReason());
        dto.setInventoryRestocked(entity.isInventoryRestocked());
        dto.setInventoryRestockStatus(entity.getInventoryRestockStatus());
        dto.setInventoryRestockFailureReason(entity.getInventoryRestockFailureReason());
        dto.setIdempotencyKey(entity.getIdempotencyKey());
        dto.setRequestedAt(entity.getRequestedAt());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setRejectedAt(entity.getRejectedAt());
        dto.setReceivedAt(entity.getReceivedAt());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
