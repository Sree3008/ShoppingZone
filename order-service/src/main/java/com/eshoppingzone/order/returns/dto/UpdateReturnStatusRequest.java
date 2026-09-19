package com.eshoppingzone.order.returns.dto;

import com.eshoppingzone.order.returns.enums.ReturnStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateReturnStatusRequest {

    @NotNull(message = "Return status is required")
    private ReturnStatus status;

    public UpdateReturnStatusRequest() {
    }

    public UpdateReturnStatusRequest(ReturnStatus status) {
        this.status = status;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public void setStatus(ReturnStatus status) {
        this.status = status;
    }
}
