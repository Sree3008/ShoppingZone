package com.eshoppingzone.order.returns.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReturnEvent implements Serializable {

    private String eventId;
    private String eventType;
    private Long returnId;
    private String returnNumber;
    private Long orderId;
    private Long customerId;
    private String status;
    private BigDecimal refundAmount;
    private LocalDateTime timestamp;

    public ReturnEvent() {
    }

    public ReturnEvent(String eventId, String eventType, Long returnId, String returnNumber, Long orderId, Long customerId, String status, BigDecimal refundAmount, LocalDateTime timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.returnId = returnId;
        this.returnNumber = returnNumber;
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.refundAmount = refundAmount;
        this.timestamp = timestamp;
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

    public Long getReturnId() {
        return returnId;
    }

    public void setReturnId(Long returnId) {
        this.returnId = returnId;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
