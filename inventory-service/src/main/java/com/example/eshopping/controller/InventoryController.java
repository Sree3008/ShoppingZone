package com.example.eshopping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @PostMapping
    public ResponseEntity<String> createInventory() {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Inventory initialized successfully");
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<String> getInventoryByProductId() {

        return ResponseEntity.ok(
                "Inventory retrieved successfully"
        );
    }

    @PostMapping("/product/{productId}/add")
    public ResponseEntity<String> addStock() {

        return ResponseEntity.ok(
                "Stock increased successfully"
        );
    }

    @PostMapping("/product/{productId}/reduce")
    public ResponseEntity<String> reduceStock() {

        return ResponseEntity.ok(
                "Stock reduced successfully"
        );
    }

    @PostMapping("/reserve")
    public ResponseEntity<String> reserveStock() {

        return ResponseEntity.ok(
                "Stock reserved successfully"
        );
    }

    @PostMapping("/release")
    public ResponseEntity<String> releaseStock() {

        return ResponseEntity.ok(
                "Stock released successfully"
        );
    }

    @PostMapping("/confirm")
    public ResponseEntity<String> confirmStock() {

        return ResponseEntity.ok(
                "Stock consumption confirmed successfully"
        );
    }

    @GetMapping("/product/{productId}/movements")
    public ResponseEntity<String> getMovements() {

        return ResponseEntity.ok(
                "Stock movements retrieved successfully"
        );
    }
}
