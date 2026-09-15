package com.eshoppingzone.product.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public String getAllProducts() {
        return "Products list retrieved successfully";
    }

    @GetMapping("/{id}")
    public String getProductById(@PathVariable Long id) {
        return "Product details retrieved successfully for ID: " + id;
    }

    @PostMapping
    public String createProduct() {
        return "Product created successfully";
    }

    @PutMapping("/{id}")
    public String updateProduct(@PathVariable Long id) {
        return "Product updated successfully for ID: " + id;
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) {
        return "Product deleted successfully for ID: " + id;
    }

    @GetMapping("/categories")
    public String getCategories() {
        return "Categories list retrieved successfully";
    }

    @GetMapping("/{id}/internal")
    public String getProductInternal(@PathVariable Long id) {
        return "Internal product details retrieved successfully for ID: " + id;
    }
}