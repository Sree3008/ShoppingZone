package com.example.eshoppingzone.controller;

import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	@PostMapping("/register")
	public String register() {
		return "register";
	}

	@PostMapping("/login")
	public String login() {
		return "login";	}

	@GetMapping("/me")
	public String getCurrentUser(Authentication authentication) {
		return "me";	}

	@PostMapping("/forgot-password")
	public String forgotPassword() {
		return "forgot-password";
	}

	@PostMapping("/reset-password")
	    public String resetPassword() {
	        return "ResetPassword";
	    }
}