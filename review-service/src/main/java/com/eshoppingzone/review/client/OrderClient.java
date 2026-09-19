package com.eshoppingzone.review.client;

import com.eshoppingzone.review.dto.ApiResponse;
import com.eshoppingzone.review.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/v1/orders/{id}/internal")
    ApiResponse<OrderDto> getOrderInternal(@PathVariable("id") Long id);
}
