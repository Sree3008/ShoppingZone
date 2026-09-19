package com.eshoppingzone.product.security;

import com.eshoppingzone.product.controller.ProductController;
import com.eshoppingzone.product.dto.CategoryDto;
import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.exception.GlobalExceptionHandler;
import com.eshoppingzone.product.messaging.ProductViewEventPublisher;
import com.eshoppingzone.product.service.ProductService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class ProductInternalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductViewEventPublisher productViewEventPublisher;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String INVALID_SECRET = "1111111111111111111111111111111111111111111111111111111111111111";

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(String sub, Long userId, String role, SecretKey key) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        return Jwts.builder()
                .subject(sub)
                .claim("userId", userId)
                .claim("email", sub + "@eshoppingzone.com")
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("1. Anonymous request to Product internal endpoint returns 401 Unauthorized")
    void testAnonymousRequestToInternalProductEndpointRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products/1/internal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("2. Customer JWT cannot access Product internal endpoint (403 Forbidden)")
    void testCustomerCannotAccessInternalProductEndpoint() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", signingKey);

        mockMvc.perform(get("/api/v1/products/1/internal")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Valid internal JWT from review-service can access Product internal endpoint (200 OK)")
    void testValidReviewServiceInternalTokenCanAccessInternalProductEndpoint() throws Exception {
        String internalToken = generateToken("internal-review-service", null, "INTERNAL", signingKey);

        ProductDto productDto = new ProductDto(1L, 5L, "Smartphone", "Latest phone", "Electronics",
                new BigDecimal("699.99"), "http://image.url", ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());

        when(productService.getProductInternal(1L)).thenReturn(productDto);

        mockMvc.perform(get("/api/v1/products/1/internal")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Smartphone"));
    }

    @Test
    @DisplayName("4. Invalid internal JWT is rejected (401 Unauthorized)")
    void testInvalidInternalTokenIsRejected() throws Exception {
        SecretKey invalidKey = Keys.hmacShaKeyFor(INVALID_SECRET.getBytes(StandardCharsets.UTF_8));
        String invalidToken = generateToken("internal-review-service", null, "INTERNAL", invalidKey);

        mockMvc.perform(get("/api/v1/products/1/internal")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Normal public product browsing still works anonymously without token")
    void testPublicProductBrowsingWorksAnonymously() throws Exception {
        ProductDto productDto = new ProductDto(1L, 5L, "Smartphone", "Latest phone", "Electronics",
                new BigDecimal("699.99"), "http://image.url", ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());

        when(productService.getActiveProducts(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(productDto)));
        when(productService.getProductById(1L)).thenReturn(productDto);
        when(productService.getActiveCategories())
                .thenReturn(Collections.singletonList(new CategoryDto(1L, "Electronics", "Electronics category", true, LocalDateTime.now())));

        // 5a. List products
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        // 5b. Get product by ID
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));

        // 5c. Get active categories
        mockMvc.perform(get("/api/v1/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Electronics"));
    }
}
