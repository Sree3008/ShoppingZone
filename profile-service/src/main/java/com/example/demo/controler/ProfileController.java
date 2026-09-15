package com.example.demo.controler;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

	@GetMapping("/me")

	public String register() {
		return "me";
	}

	@PutMapping("/me")
	public String login() {
		return "add me";	}

	 @GetMapping("/user/{userId}")
	public String getCurrentUser(Authentication authentication) {
		return "user";	}

	 @PostMapping("/addresses")

	public String forgotPassword() {
		return "address";
	}

	 @GetMapping("/addresses")

	    public String resetPassword() {
	        return "get address";
	    }
	 }
