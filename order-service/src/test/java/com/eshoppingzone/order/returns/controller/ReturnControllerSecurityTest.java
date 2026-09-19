package com.eshoppingzone.order.returns.controller;

import com.eshoppingzone.order.exception.GlobalExceptionHandler;
import com.eshoppingzone.order.exception.ResourceNotFoundException;
import com.eshoppingzone.order.returns.dto.CreateReturnRequest;
import com.eshoppingzone.order.returns.dto.ReturnDto;
import com.eshoppingzone.order.returns.enums.ReturnReason;
import com.eshoppingzone.order.returns.enums.ReturnStatus;
import com.eshoppingzone.order.returns.service.ReturnService;
import com.eshoppingzone.order.security.JwtAuthenticationFilter;
import com.eshoppingzone.order.security.JwtTokenProvider;
import com.eshoppingzone.order.security.SecurityConfig;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = ReturnController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class ReturnControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReturnService returnService;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("email", username + "@eshoppingzone.com")
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // 1. Customer can create/view own return
    @Test
    @DisplayName("1. Customer can create return and view own returns")
    void testCustomerCanCreateAndViewOwnReturns() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        ReturnDto dto = new ReturnDto();
        dto.setId(1L);
        dto.setReturnNumber("RET-12345678");
        dto.setOrderId(10L);
        dto.setCustomerId(50L);
        dto.setStatus(ReturnStatus.RETURN_REQUESTED);
        dto.setRefundAmount(new BigDecimal("299.99"));

        when(returnService.createReturn(eq(50L), any(CreateReturnRequest.class), any())).thenReturn(dto);
        when(returnService.getCustomerReturns(50L)).thenReturn(List.of(dto));
        when(returnService.getCustomerReturnById(1L, 50L)).thenReturn(dto);

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Defective screen");

        // Customer can create return
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.returnNumber").value("RET-12345678"));

        // Customer can view own returns
        mockMvc.perform(get("/api/v1/returns/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].returnNumber").value("RET-12345678"));

        // Customer can view own return by ID
        mockMvc.perform(get("/api/v1/returns/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.returnNumber").value("RET-12345678"));
    }

    // 2. Customer cannot view another customer's return
    @Test
    @DisplayName("2. Customer cannot view another customer's return")
    void testCustomerCannotViewAnotherCustomerReturn() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        // Service throws ResourceNotFoundException when customer attempts to access another user's return
        when(returnService.getCustomerReturnById(99L, 50L))
                .thenThrow(new ResourceNotFoundException("Return not found with id: 99"));

        mockMvc.perform(get("/api/v1/returns/99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 3. Customer cannot access admin APIs
    @Test
    @DisplayName("3. Customer cannot access admin APIs")
    void testCustomerCannotAccessAdminApis() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        mockMvc.perform(get("/api/v1/admin/returns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/returns/1/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // 4. Admin can access admin APIs
    @Test
    @DisplayName("4. Admin can access admin APIs")
    void testAdminCanAccessAdminApis() throws Exception {
        String adminToken = generateToken(1L, "admin", "ADMIN");

        when(returnService.getAllReturns(null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/returns")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // 5. Unauthenticated requests are rejected
    @Test
    @DisplayName("5. Unauthenticated requests are rejected with 401")
    void testUnauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/returns/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/returns"))
                .andExpect(status().isUnauthorized());

        CreateReturnRequest request = new CreateReturnRequest(10L, 100L, 1, ReturnReason.DEFECTIVE, "Defective screen");
        mockMvc.perform(post("/api/v1/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
