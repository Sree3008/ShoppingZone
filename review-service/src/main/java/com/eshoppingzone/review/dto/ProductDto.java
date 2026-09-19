package com.eshoppingzone.review.dto;

import java.math.BigDecimal;

public class ProductDto {
    private Long id;
    private Long merchantId;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String imageUrl;
    private String status;

    public ProductDto() {
    }

    public ProductDto(Long id, Long merchantId, String name, String description, String category, BigDecimal price, String imageUrl, String status) {
        this.id = id;
        this.merchantId = merchantId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.imageUrl = imageUrl;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
