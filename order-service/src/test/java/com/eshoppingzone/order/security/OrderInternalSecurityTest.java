package com.eshoppingzone.order.security;

import com.eshoppingzone.order.controller.OrderController;
import com.eshoppingzone.order.dto.OrderDto;
import com.eshoppingzone.order.entity.OrderStatus;
import com.eshoppingzone.order.exception.GlobalExceptionHandler;
import com.eshoppingzone.order.service.OrderService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class OrderInternalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

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
    @DisplayName("1. Anonymous request to Order internal endpoint returns 401 Unauthorized")
    void testAnonymousRequestToInternalOrderEndpointRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1/internal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("2. Customer JWT cannot access Order internal endpoint (403 Forbidden)")
    void testCustomerCannotAccessInternalOrderEndpoint() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", signingKey);

        mockMvc.perform(get("/api/v1/orders/1/internal")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Valid internal JWT from review-service can access Order internal endpoint (200 OK)")
    void testValidReviewServiceInternalTokenCanAccessInternalOrderEndpoint() throws Exception {
        String internalToken = generateToken("internal-review-service", null, "INTERNAL", signingKey);

        OrderDto orderDto = new OrderDto();
        orderDto.setId(1L);
        orderDto.setCustomerId(10L);
        orderDto.setStatus(OrderStatus.DELIVERED);

        when(orderService.getOrderInternal(1L)).thenReturn(orderDto);

        mockMvc.perform(get("/api/v1/orders/1/internal")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("4. Invalid internal JWT is rejected (401 Unauthorized)")
    void testInvalidInternalTokenIsRejected() throws Exception {
        SecretKey invalidKey = Keys.hmacShaKeyFor(INVALID_SECRET.getBytes(StandardCharsets.UTF_8));
        String invalidToken = generateToken("internal-review-service", null, "INTERNAL", invalidKey);

        mockMvc.perform(get("/api/v1/orders/1/internal")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Normal authenticated customer order APIs still work")
    void testAuthenticatedCustomerCanAccessCustomerOrderApis() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", signingKey);

        OrderDto orderDto = new OrderDto();
        orderDto.setId(1L);
        orderDto.setCustomerId(10L);
        orderDto.setStatus(OrderStatus.DELIVERED);

        when(orderService.getOrderById(1L, 10L)).thenReturn(orderDto);
        when(orderService.getCustomerOrders(10L)).thenReturn(Collections.singletonList(orderDto));

        // Customer can get specific order
        mockMvc.perform(get("/api/v1/orders/1")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));

        // Customer can list own orders
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("6. Anonymous request to customer order APIs is rejected (401 Unauthorized)")
    void testAnonymousRequestToCustomerOrderApisRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Customer attempting to access another customer's order is rejected (IDOR protection)")
    void testCustomerAttemptingToAccessAnotherCustomerOrderRejected() throws Exception {
        String customerAToken = generateToken("customerA", 10L, "CUSTOMER", signingKey);

        when(orderService.getOrderById(eq(99L), eq(10L)))
                .thenThrow(new com.eshoppingzone.order.exception.ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/v1/orders/99")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
