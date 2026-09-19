package com.eshoppingzone.cart.security;

import com.eshoppingzone.cart.controller.CartController;
import com.eshoppingzone.cart.dto.CartDto;
import com.eshoppingzone.cart.exception.GlobalExceptionHandler;
import com.eshoppingzone.cart.service.CartService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class CartSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String generateToken(String subject, Long userId, String role, long expiryOffsetMs) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claims(Map.of("userId", userId, "email", subject + "@eshoppingzone.com", "role", role, "type", "ACCESS"))
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() + expiryOffsetMs))
                .signWith(key)
                .compact();
    }

    @Test
    void getMyCart_withoutToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("org.springframework"))));
    }

    @Test
    void getMyCart_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyCart_withExpiredToken_shouldReturn401WithSafeMessage() throws Exception {
        String expiredToken = generateToken("customer1", 10L, "CUSTOMER", -5000);
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ExpiredJwtException"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyCart_withCustomerToken_shouldReturn200() throws Exception {
        String token = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        CartDto mockCart = new CartDto();
        mockCart.setId(1L);
        mockCart.setCustomerId(10L);
        when(cartService.getMyCart(eq(10L))).thenReturn(mockCart);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCartByCustomerId_withCustomerToken_shouldReturn403WithSafeMessage() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        mockMvc.perform(get("/api/v1/cart/customer/99")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getCartByCustomerId_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("order-service", 0L, "INTERNAL", 3600000);
        CartDto mockCart = new CartDto();
        mockCart.setId(2L);
        mockCart.setCustomerId(99L);
        when(cartService.getCartByCustomerId(eq(99L))).thenReturn(mockCart);

        mockMvc.perform(get("/api/v1/cart/customer/99")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void clearCustomerCart_withCustomerToken_shouldReturn403() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        mockMvc.perform(delete("/api/v1/cart/customer/99/clear")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void clearCustomerCart_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("order-service", 0L, "INTERNAL", 3600000);
        doNothing().when(cartService).clearCart(eq(99L));

        mockMvc.perform(delete("/api/v1/cart/customer/99/clear")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
