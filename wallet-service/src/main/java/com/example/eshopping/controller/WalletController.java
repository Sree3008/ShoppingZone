package com.example.eshopping.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    @GetMapping
    public ResponseEntity<String> getMyWallet() {

        return ResponseEntity.ok(
                "Wallet retrieved successfully"
        );
    }

    @GetMapping("/balance")
    public ResponseEntity<String> getBalance() {

        return ResponseEntity.ok(
                "Balance retrieved successfully"
        );
    }

    @PostMapping("/topup")
    public ResponseEntity<String> topUp() {

        return ResponseEntity.ok(
                "Wallet topped up successfully"
        );
    }

    @GetMapping("/transactions")
    public ResponseEntity<String> getTransactions() {

        return ResponseEntity.ok(
                "Wallet transactions retrieved successfully"
        );
    }

    @PostMapping("/debit")
    public ResponseEntity<String> debit() {

        return ResponseEntity.ok(
                "Wallet debited successfully"
        );
    }

    @PostMapping("/credit")
    public ResponseEntity<String> credit() {

        return ResponseEntity.ok(
                "Wallet credited successfully"
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<String> getWalletByUserId() {

        return ResponseEntity.ok(
                "Wallet retrieved successfully"
        );
    }
}
