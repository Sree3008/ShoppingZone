package com.eshoppingzone.order.returns.controller;

import com.eshoppingzone.order.dto.ApiResponse;
import com.eshoppingzone.order.returns.dto.CreateReturnRequest;
import com.eshoppingzone.order.returns.dto.RejectReturnRequest;
import com.eshoppingzone.order.returns.dto.ReturnDto;
import com.eshoppingzone.order.returns.dto.UpdateReturnStatusRequest;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import com.eshoppingzone.order.returns.service.ReturnService;
import com.eshoppingzone.order.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Return Management", description = "Customer and Admin APIs for managing order returns and refunds")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    private Long getCustomerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    // ==========================================
    // Customer APIs
    // ==========================================

    @PostMapping("/api/v1/returns")
    @Operation(summary = "Create Return Request", description = "Customer initiates a return request for a delivered order item")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnDto>> createReturn(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateReturnRequest request) {
        Long customerId = getCustomerId(authentication);
        ReturnDto returnDto = returnService.createReturn(customerId, request, idempotencyKey);
        return new ResponseEntity<>(ApiResponse.success("Return request created successfully", returnDto), HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/returns/my")
    @Operation(summary = "Get My Returns", description = "Customer retrieves their own returns")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<ReturnDto>>> getMyReturns(Authentication authentication) {
        Long customerId = getCustomerId(authentication);
        List<ReturnDto> returns = returnService.getCustomerReturns(customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer returns retrieved successfully", returns));
    }

    @GetMapping("/api/v1/returns/{id}")
    @Operation(summary = "Get Return by ID", description = "Customer retrieves details of a specific return request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnDto>> getReturnById(
            Authentication authentication,
            @PathVariable Long id) {
        Long customerId = getCustomerId(authentication);
        ReturnDto returnDto = returnService.getCustomerReturnById(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Return retrieved successfully", returnDto));
    }

    @PutMapping("/api/v1/returns/{id}/cancel")
    @Operation(summary = "Cancel Return Request", description = "Customer cancels a pending return request")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReturnDto>> cancelReturn(
            Authentication authentication,
            @PathVariable Long id) {
        Long customerId = getCustomerId(authentication);
        ReturnDto returnDto = returnService.cancelReturn(id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Return request cancelled successfully", returnDto));
    }

    // ==========================================
    // Admin APIs
    // ==========================================

    @GetMapping("/api/v1/admin/returns")
    @Operation(summary = "Get All Returns", description = "Admin lists all returns with optional status filter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReturnDto>>> getAllReturns(
            @RequestParam(required = false) ReturnStatus status) {
        List<ReturnDto> returns = returnService.getAllReturns(status);
        return ResponseEntity.ok(ApiResponse.success("Returns retrieved successfully", returns));
    }

    @GetMapping("/api/v1/admin/returns/pending")
    @Operation(summary = "Get Pending Returns", description = "Admin lists all pending return requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ReturnDto>>> getPendingReturns() {
        List<ReturnDto> returns = returnService.getPendingReturns();
        return ResponseEntity.ok(ApiResponse.success("Pending returns retrieved successfully", returns));
    }

    @GetMapping("/api/v1/admin/returns/{id}")
    @Operation(summary = "Admin Get Return by ID", description = "Admin retrieves details of a return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> getReturnByIdAdmin(@PathVariable Long id) {
        ReturnDto returnDto = returnService.getReturnByIdAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Return retrieved successfully", returnDto));
    }

    @PutMapping("/api/v1/admin/returns/{id}/approve")
    @Operation(summary = "Approve Return", description = "Admin approves a return request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> approveReturn(@PathVariable Long id) {
        ReturnDto returnDto = returnService.approveReturn(id);
        return ResponseEntity.ok(ApiResponse.success("Return approved successfully", returnDto));
    }

    @PutMapping("/api/v1/admin/returns/{id}/reject")
    @Operation(summary = "Reject Return", description = "Admin rejects a return request with reason")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> rejectReturn(
            @PathVariable Long id,
            @Valid @RequestBody RejectReturnRequest request) {
        ReturnDto returnDto = returnService.rejectReturn(id, request);
        return ResponseEntity.ok(ApiResponse.success("Return rejected successfully", returnDto));
    }

    @PutMapping("/api/v1/admin/returns/{id}/status")
    @Operation(summary = "Update Return Status", description = "Admin transitions return status along the state machine")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> updateReturnStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReturnStatusRequest request) {
        ReturnDto returnDto = returnService.updateReturnStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Return status updated successfully", returnDto));
    }

    @PostMapping("/api/v1/admin/returns/{id}/retry-restock")
    @Operation(summary = "Retry Restock", description = "Admin retries failed inventory restocking for a received return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> retryRestock(@PathVariable Long id) {
        ReturnDto returnDto = returnService.retryRestock(id);
        return ResponseEntity.ok(ApiResponse.success("Inventory restock retried successfully", returnDto));
    }

    @PostMapping("/api/v1/admin/returns/{id}/retry-refund")
    @Operation(summary = "Retry Refund", description = "Admin retries failed refund initiation for a completed return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnDto>> retryRefund(@PathVariable Long id) {
        ReturnDto returnDto = returnService.retryRefund(id);
        return ResponseEntity.ok(ApiResponse.success("Refund initiation retried successfully", returnDto));
    }
}
