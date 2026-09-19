package com.eshoppingzone.payment.security;

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
        if (!template.headers().containsKey("Authorization")) {
            template.header("Authorization", "Bearer " + internalTokenService.generateInternalToken());
        }
    }
}
