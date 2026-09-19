package com.eshoppingzone.review.client;

import com.eshoppingzone.review.dto.ApiResponse;
import com.eshoppingzone.review.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}/internal")
    ApiResponse<ProductDto> getProductInternal(@PathVariable("id") Long id);
}
