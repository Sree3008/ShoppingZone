package com.eshoppingzone.payment.security;

import com.eshoppingzone.payment.controller.RefundController;
import com.eshoppingzone.payment.dto.RefundDto;
import com.eshoppingzone.payment.dto.RefundRequest;
import com.eshoppingzone.payment.service.PaymentService;
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
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RefundController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class RefundInternalSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

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
    @DisplayName("Customer JWT cannot access /api/v1/refunds/internal (403 Forbidden)")
    void testCustomerCannotAccessInternalRefund() throws Exception {
        String customerToken = generateToken("customer1", "CUSTOMER");
        RefundRequest request = new RefundRequest(10L, new BigDecimal("299.99"), "Customer return");

        mockMvc.perform(post("/api/v1/refunds/internal")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Internal JWT can access /api/v1/refunds/internal (200 OK)")
    void testInternalTokenCanAccessInternalRefund() throws Exception {
        String internalToken = generateToken("internal-order-service", "INTERNAL");
        RefundRequest request = new RefundRequest(10L, new BigDecimal("299.99"), "Customer return");

        RefundDto refundDto = new RefundDto();
        refundDto.setId(101L);
        refundDto.setRefundReference("REF-101");
        when(paymentService.requestRefundInternal(any(RefundRequest.class))).thenReturn(refundDto);

        mockMvc.perform(post("/api/v1/refunds/internal")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
