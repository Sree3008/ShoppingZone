package com.eshoppingzone.order.returns.dto;

import jakarta.validation.constraints.NotBlank;

public class RejectReturnRequest {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;

    public RejectReturnRequest() {
    }

    public RejectReturnRequest(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
