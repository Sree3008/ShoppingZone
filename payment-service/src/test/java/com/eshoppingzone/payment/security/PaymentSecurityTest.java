package com.eshoppingzone.payment.security;

import com.eshoppingzone.payment.controller.PaymentController;
import com.eshoppingzone.payment.dto.PaymentDto;
import com.eshoppingzone.payment.dto.ProcessPaymentRequest;
import com.eshoppingzone.payment.entity.PaymentMethod;
import com.eshoppingzone.payment.entity.PaymentStatus;
import com.eshoppingzone.payment.exception.GlobalExceptionHandler;
import com.eshoppingzone.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class PaymentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

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
    void getPaymentByOrderId_withoutToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getPaymentByOrderId_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order/100")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getPaymentByOrderId_withExpiredToken_shouldReturn401WithSafeMessage() throws Exception {
        String expiredToken = generateToken("customer1", 10L, "CUSTOMER", -5000);
        mockMvc.perform(get("/api/v1/payments/order/100")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ExpiredJwtException"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void processPayment_withCustomerToken_shouldReturn403WithSafeMessage() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        ProcessPaymentRequest request = new ProcessPaymentRequest(100L, 10L, BigDecimal.valueOf(150.00), PaymentMethod.WALLET);

        mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void processPayment_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("order-service", 0L, "INTERNAL", 3600000);
        ProcessPaymentRequest request = new ProcessPaymentRequest(100L, 10L, BigDecimal.valueOf(150.00), PaymentMethod.WALLET);

        PaymentDto mockPayment = new PaymentDto();
        mockPayment.setId(1L);
        mockPayment.setOrderId(100L);
        mockPayment.setCustomerId(10L);
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.processPayment(any(), any())).thenReturn(mockPayment);

        mockMvc.perform(post("/api/v1/payments/process")
                        .header("Authorization", "Bearer " + internalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getPaymentByOrderId_whenCustomerDoesNotOwnPayment_shouldReturn403Idor() throws Exception {
        String customerAToken = generateToken("customerA", 10L, "CUSTOMER", 3600000);

        PaymentDto mockPayment = new PaymentDto();
        mockPayment.setId(1L);
        mockPayment.setOrderId(200L);
        mockPayment.setCustomerId(99L); // Belongs to Customer B
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.getPaymentByOrderId(eq(200L))).thenReturn(mockPayment);

        mockMvc.perform(get("/api/v1/payments/order/200")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPaymentByOrderId_whenCustomerOwnsPayment_shouldReturn200() throws Exception {
        String customerAToken = generateToken("customerA", 10L, "CUSTOMER", 3600000);

        PaymentDto mockPayment = new PaymentDto();
        mockPayment.setId(1L);
        mockPayment.setOrderId(200L);
        mockPayment.setCustomerId(10L); // Belongs to Customer A
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.getPaymentByOrderId(eq(200L))).thenReturn(mockPayment);

        mockMvc.perform(get("/api/v1/payments/order/200")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getPaymentByOrderId_whenAdminAccessesAnyPayment_shouldReturn200() throws Exception {
        String adminToken = generateToken("adminUser", 1L, "ADMIN", 3600000);

        PaymentDto mockPayment = new PaymentDto();
        mockPayment.setId(1L);
        mockPayment.setOrderId(200L);
        mockPayment.setCustomerId(99L);
        mockPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentService.getPaymentByOrderId(eq(200L))).thenReturn(mockPayment);

        mockMvc.perform(get("/api/v1/payments/order/200")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
