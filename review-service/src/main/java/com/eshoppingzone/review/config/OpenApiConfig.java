package com.eshoppingzone.review.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reviewOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Review & Rating Service API")
                        .description("Product Review and Rating Management API for EShoppingZone Platform")
                        .version("1.0.0"));
    }
}
