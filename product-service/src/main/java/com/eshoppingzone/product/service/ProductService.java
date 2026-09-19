package com.eshoppingzone.product.service;

import com.eshoppingzone.product.dto.*;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    Page<ProductDto> getActiveProducts(String category, String keyword, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    List<ProductDto> getActiveProducts(String category, String keyword, BigDecimal minPrice, BigDecimal maxPrice);
    ProductDto getProductById(Long id);
    ProductDto getProductInternal(Long id);

    ProductDto createProduct(Long merchantId, CreateProductRequest request);
    ProductDto updateProduct(Long id, Long merchantId, UpdateProductRequest request);
    List<ProductDto> getMerchantProducts(Long merchantId);
    ProductDto getMerchantProductById(Long id, Long merchantId);
    void deactivateMerchantProduct(Long id, Long merchantId);

    List<ProductDto> getPendingProducts();
    ProductDto approveProduct(Long id);
    ProductDto rejectProduct(Long id);
    ProductDto deactivateProductByAdmin(Long id);
    List<ProductDto> getAllProductsAdmin();

    CategoryDto createCategory(CategoryRequest request);
    CategoryDto updateCategory(Long id, CategoryRequest request);
    List<CategoryDto> getActiveCategories();
    List<CategoryDto> getAllCategoriesAdmin();
}
