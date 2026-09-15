package com.eshoppingzone.delivery.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    @GetMapping
    public String getAllDeliveries() {
        return "Deliveries list retrieved successfully";
    }

    @GetMapping("/{id}")
    public String getDeliveryById(@PathVariable Long id) {
        return "Delivery details retrieved successfully for ID: " + id;
    }

    @PostMapping
    public String createDelivery() {
        return "Delivery scheduled successfully";
    }

    @PutMapping("/{id}/status")
    public String updateDeliveryStatus(@PathVariable Long id) {
        return "Delivery status updated successfully for ID: " + id;
    }

    @DeleteMapping("/{id}")
    public String cancelDelivery(@PathVariable Long id) {
        return "Delivery cancelled successfully for ID: " + id;
    }
}
