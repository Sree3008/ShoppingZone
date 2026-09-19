package com.eshoppingzone.review.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderDto {
    private Long id;
    private String orderNumber;
    private Long customerId;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemDto> items = new ArrayList<>();

    public OrderDto() {
    }

    public OrderDto(Long id, String orderNumber, Long customerId, String status, List<OrderItemDto> items) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.status = status;
        this.items = items != null ? items : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
