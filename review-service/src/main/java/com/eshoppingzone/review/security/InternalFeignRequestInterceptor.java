package com.eshoppingzone.review.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class InternalFeignRequestInterceptor implements RequestInterceptor {

    private final InternalTokenService internalTokenService;

    public InternalFeignRequestInterceptor(InternalTokenService internalTokenService) {
        this.internalTokenService = internalTokenService;
    }

    @Override
    public void apply(RequestTemplate template) {
        String token = internalTokenService.generateInternalToken();
        template.header("Authorization", "Bearer " + token);
    }
}
