package com.eshoppingzone.order.returns.dto;

import com.eshoppingzone.order.returns.enums.ReturnReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateReturnRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Order Item ID is required")
    private Long orderItemId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Integer quantity;

    @NotNull(message = "Return reason is required")
    private ReturnReason reason;

    private String description;

    public CreateReturnRequest() {
    }

    public CreateReturnRequest(Long orderId, Long orderItemId, Integer quantity, ReturnReason reason, String description) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.reason = reason;
        this.description = description;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public ReturnReason getReason() {
        return reason;
    }

    public void setReason(ReturnReason reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
