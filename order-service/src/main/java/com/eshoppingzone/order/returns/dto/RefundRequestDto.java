package com.eshoppingzone.order.returns.dto;

import java.math.BigDecimal;

public class RefundRequestDto {

    private Long orderId;
    private BigDecimal amount;
    private String reason;

    public RefundRequestDto() {
    }

    public RefundRequestDto(Long orderId, BigDecimal amount, String reason) {
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
