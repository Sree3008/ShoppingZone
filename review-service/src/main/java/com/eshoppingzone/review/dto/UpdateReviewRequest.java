package com.eshoppingzone.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateReviewRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @NotBlank(message = "Comment cannot be blank")
    @Size(min = 3, max = 2000, message = "Comment must be between 3 and 2000 characters")
    private String comment;

    public UpdateReviewRequest() {
    }

    public UpdateReviewRequest(Integer rating, String title, String comment) {
        this.rating = rating;
        this.title = title;
        this.comment = comment;
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
}
