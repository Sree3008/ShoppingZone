package com.example.eshoppingzone.controller;


import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth/admin/users")

public class AdminUserController {

    @PostMapping
    public String createUser() {
    	return "create user";
    }

    @GetMapping
    public String getAllUsers() {
        return "get all users";

    }

    @GetMapping("/{id}")
    public String getUserById(@PathVariable Long id) {
        return "get by id";

    }

    @PutMapping("/{id}/status")
    public String updateUserStatus() {
    	return "status";
    }
                                                                  
}
