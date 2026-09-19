package com.eshoppingzone.review.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProductRatingSummaryDto {
    private Long productId;
    private Double averageRating;
    private Long totalReviews;
    private Map<String, Long> ratingDistribution;

    public ProductRatingSummaryDto() {
        this.ratingDistribution = new LinkedHashMap<>();
    }

    public ProductRatingSummaryDto(Long productId, Double averageRating, Long totalReviews, Map<String, Long> ratingDistribution) {
        this.productId = productId;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.ratingDistribution = ratingDistribution != null ? ratingDistribution : new LinkedHashMap<>();
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Long getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Long totalReviews) {
        this.totalReviews = totalReviews;
    }

    public Map<String, Long> getRatingDistribution() {
        return ratingDistribution;
    }

    public void setRatingDistribution(Map<String, Long> ratingDistribution) {
        this.ratingDistribution = ratingDistribution;
    }
}
