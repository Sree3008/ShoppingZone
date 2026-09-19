package com.eshoppingzone.inventory.security;

import com.eshoppingzone.inventory.controller.InventoryController;
import com.eshoppingzone.inventory.dto.StockReservationItem;
import com.eshoppingzone.inventory.dto.StockReservationRequest;
import com.eshoppingzone.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eshoppingzone.inventory.exception.GlobalExceptionHandler;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = InventoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class InventoryRestockSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(String sub, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        return Jwts.builder()
                .subject(sub)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Customer JWT cannot access /api/v1/inventory/restock (403 Forbidden)")
    void testCustomerCannotAccessRestock() throws Exception {
        String customerToken = generateToken("customer1", "CUSTOMER");
        StockReservationRequest request = new StockReservationRequest("RET-12345", List.of(new StockReservationItem(1L, 2)));

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal JWT can access /api/v1/inventory/restock (200 OK)")
    void testInternalTokenCanAccessRestock() throws Exception {
        String internalToken = generateToken("internal-order-service", "INTERNAL");
        StockReservationRequest request = new StockReservationRequest("RET-12345", List.of(new StockReservationItem(1L, 2)));

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Customer JWT cannot access /api/v1/inventory/reserve (403 Forbidden)")
    void testCustomerCannotAccessReserve() throws Exception {
        String customerToken = generateToken("customer1", "CUSTOMER");
        StockReservationRequest request = new StockReservationRequest("ORD-123", List.of(new StockReservationItem(1L, 2)));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal JWT can access /api/v1/inventory/reserve (200 OK)")
    void testInternalTokenCanAccessReserve() throws Exception {
        String internalToken = generateToken("internal-order-service", "INTERNAL");
        StockReservationRequest request = new StockReservationRequest("ORD-123", List.of(new StockReservationItem(1L, 2)));

        mockMvc.perform(post("/api/v1/inventory/reserve")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
