package com.eshoppingzone.recommendation.security;

import com.eshoppingzone.recommendation.controller.RecommendationController;
import com.eshoppingzone.recommendation.entity.CustomerProductView;
import com.eshoppingzone.recommendation.exception.GlobalExceptionHandler;
import com.eshoppingzone.recommendation.service.RecommendationService;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecommendationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class RecommendationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

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
    void getRecommendations_withoutToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getRecommendations_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getRecommendations_withExpiredToken_shouldReturn401WithSafeMessage() throws Exception {
        String expiredToken = generateToken("customer1", 10L, "CUSTOMER", -5000);
        mockMvc.perform(get("/api/v1/recommendations")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ExpiredJwtException"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getRecommendations_withValidCustomerToken_shouldReturn200() throws Exception {
        String token = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        CustomerProductView view = new CustomerProductView(10L, 501L, LocalDateTime.now());
        when(recommendationService.getRecommendations(eq(10L))).thenReturn(List.of(view));

        mockMvc.perform(get("/api/v1/recommendations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].productId").value(501));
    }
}
