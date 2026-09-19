package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ApiResponse;
import com.eshoppingzone.product.dto.CategoryDto;
import com.eshoppingzone.product.dto.PagedResponse;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.service.ProductService;
import com.eshoppingzone.product.security.UserPrincipal;
import com.eshoppingzone.product.messaging.ProductViewEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Browsing", description = "Public / Customer APIs for browsing products and categories")
public class ProductController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "name", "createdAt", "id", "category");

    private final ProductService productService;
    private final ProductViewEventPublisher productViewEventPublisher;

    public ProductController(ProductService productService) {
        this(productService, null);
    }

    @Autowired
    public ProductController(ProductService productService, ProductViewEventPublisher productViewEventPublisher) {
        this.productService = productService;
        this.productViewEventPublisher = productViewEventPublisher;
    }

    @GetMapping({"", "/search"})
    @Operation(summary = "Browse Active Products", description = "Search, filter, sort and paginate active approved products")
    public ResponseEntity<ApiResponse<PagedResponse<ProductDto>>> getActiveProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero");
        }
        if (size > 50) {
            throw new IllegalArgumentException("Page size must not exceed 50");
        }
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum price cannot be negative");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maximum price cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price");
        }

        String normalizedSortBy = sortBy != null ? sortBy.trim() : "createdAt";
        if (!ALLOWED_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new IllegalArgumentException("Invalid sort field: '" + sortBy + "'. Allowed sort fields are: " + ALLOWED_SORT_FIELDS);
        }

        String normalizedDir = sortDirection != null ? sortDirection.trim().toLowerCase() : "desc";
        if (!"asc".equals(normalizedDir) && !"desc".equals(normalizedDir)) {
            throw new IllegalArgumentException("Invalid sort direction: '" + sortDirection + "'. Allowed values are: 'asc', 'desc'");
        }

        Sort.Direction direction = "asc".equals(normalizedDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = "id".equalsIgnoreCase(normalizedSortBy)
                ? Sort.by(direction, "id")
                : Sort.by(direction, normalizedSortBy).and(Sort.by(direction, "id"));

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDto> productPage = productService.getActiveProducts(category, keyword, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", PagedResponse.from(productPage)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Product Details", description = "Retrieve product details by ID (Active products only)")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id,
                                                                  Authentication authentication) {
        ProductDto product = productService.getProductById(id);
        if (productViewEventPublisher != null
                && authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.getUserId() != null) {
            productViewEventPublisher.publish(principal.getUserId(), id);
        }
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get Active Categories", description = "List all active product categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getActiveCategories() {
        List<CategoryDto> categories = productService.getActiveCategories();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @GetMapping("/{id}/internal")
    @Operation(summary = "Get Product Internal", description = "Internal endpoint for other microservices (Order/Cart)")
    @PreAuthorize("hasAnyRole('INTERNAL', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> getProductInternal(@PathVariable Long id) {
        ProductDto product = productService.getProductInternal(id);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }
}
