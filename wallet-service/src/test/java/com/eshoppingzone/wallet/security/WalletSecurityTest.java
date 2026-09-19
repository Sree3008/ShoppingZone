package com.eshoppingzone.wallet.security;

import com.eshoppingzone.wallet.controller.WalletController;
import com.eshoppingzone.wallet.dto.WalletDto;
import com.eshoppingzone.wallet.dto.WalletTransferRequest;
import com.eshoppingzone.wallet.exception.GlobalExceptionHandler;
import com.eshoppingzone.wallet.service.WalletService;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WalletController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class WalletSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WalletService walletService;

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
    void getMyWallet_withoutToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyWallet_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/wallet")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyWallet_withExpiredToken_shouldReturn401WithSafeMessage() throws Exception {
        String expiredToken = generateToken("customer1", 10L, "CUSTOMER", -5000);
        mockMvc.perform(get("/api/v1/wallet")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ExpiredJwtException"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyWallet_withCustomerToken_shouldReturn200() throws Exception {
        String token = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        WalletDto mockWallet = new WalletDto(1L, 10L, BigDecimal.valueOf(500.00), LocalDateTime.now(), LocalDateTime.now());
        when(walletService.getWalletByUserId(eq(10L))).thenReturn(mockWallet);

        mockMvc.perform(get("/api/v1/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getWalletByUserId_withCustomerToken_shouldReturn403WithSafeMessage() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        mockMvc.perform(get("/api/v1/wallet/user/99")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getWalletByUserId_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("payment-service", 0L, "INTERNAL", 3600000);
        WalletDto mockWallet = new WalletDto(2L, 99L, BigDecimal.valueOf(100.00), LocalDateTime.now(), LocalDateTime.now());
        when(walletService.getWalletByUserId(eq(99L))).thenReturn(mockWallet);

        mockMvc.perform(get("/api/v1/wallet/user/99")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void debitWallet_withCustomerToken_shouldReturn403() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        WalletTransferRequest request = new WalletTransferRequest(10L, BigDecimal.valueOf(50.00), "REF123", "Test debit");

        mockMvc.perform(post("/api/v1/wallet/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void debitWallet_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("payment-service", 0L, "INTERNAL", 3600000);
        WalletTransferRequest request = new WalletTransferRequest(10L, BigDecimal.valueOf(50.00), "REF123", "Test debit");
        WalletDto mockWallet = new WalletDto(1L, 10L, BigDecimal.valueOf(450.00), LocalDateTime.now(), LocalDateTime.now());
        when(walletService.debit(any(), any())).thenReturn(mockWallet);

        mockMvc.perform(post("/api/v1/wallet/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void creditWallet_withCustomerToken_shouldReturn403() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        WalletTransferRequest request = new WalletTransferRequest(10L, BigDecimal.valueOf(50.00), "REF124", "Test credit");

        mockMvc.perform(post("/api/v1/wallet/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void creditWallet_withInternalToken_shouldReturn200() throws Exception {
        String internalToken = generateToken("payment-service", 0L, "INTERNAL", 3600000);
        WalletTransferRequest request = new WalletTransferRequest(10L, BigDecimal.valueOf(50.00), "REF124", "Test credit");
        WalletDto mockWallet = new WalletDto(1L, 10L, BigDecimal.valueOf(550.00), LocalDateTime.now(), LocalDateTime.now());
        when(walletService.credit(any(), any())).thenReturn(mockWallet);

        mockMvc.perform(post("/api/v1/wallet/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
