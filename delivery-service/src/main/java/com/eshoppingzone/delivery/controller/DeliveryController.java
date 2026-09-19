package com.eshoppingzone.delivery.controller;

import com.eshoppingzone.delivery.dto.ApiResponse;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.security.UserPrincipal;
import com.eshoppingzone.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Customer Delivery Tracking", description = "APIs for customers to track order delivery status")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    private Long getCustomerId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Track Delivery by Order ID", description = "Retrieve delivery and dispatch tracking for a specific order")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DELIVERY_AGENT', 'ADMIN', 'INTERNAL')")
    public ResponseEntity<ApiResponse<DeliveryDto>> getDeliveryByOrderId(Authentication authentication, @PathVariable Long orderId) {
        DeliveryDto delivery = deliveryService.getDeliveryByOrderId(orderId);
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            boolean isStaffOrInternal = principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_INTERNAL")
                            || a.getAuthority().equals("ROLE_DELIVERY")
                            || a.getAuthority().equals("ROLE_DELIVERY_AGENT"));
            if (!isStaffOrInternal && delivery.getCustomerId() != null && !delivery.getCustomerId().equals(principal.getUserId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You are not authorized to view delivery details for this order");
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Delivery tracking details retrieved", delivery));
    }

    @GetMapping("/my")
    @Operation(summary = "Get My Deliveries", description = "Customer views all their order delivery statuses")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<DeliveryDto>>> getMyDeliveries(Authentication authentication) {
        Long customerId = getCustomerId(authentication);
        List<DeliveryDto> list = deliveryService.getMyDeliveries(customerId);
        return ResponseEntity.ok(ApiResponse.success("Deliveries retrieved successfully", list));
    }
}
