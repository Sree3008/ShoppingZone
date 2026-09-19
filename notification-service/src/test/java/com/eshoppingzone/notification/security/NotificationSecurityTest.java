package com.eshoppingzone.notification.security;

import com.eshoppingzone.notification.controller.NotificationController;
import com.eshoppingzone.notification.dto.NotificationDto;
import com.eshoppingzone.notification.exception.GlobalExceptionHandler;
import com.eshoppingzone.notification.service.NotificationService;
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

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
})
class NotificationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

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
    void getMyNotifications_withoutToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyNotifications_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyNotifications_withExpiredToken_shouldReturn401WithSafeMessage() throws Exception {
        String expiredToken = generateToken("customer1", 10L, "CUSTOMER", -5000);
        mockMvc.perform(get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ExpiredJwtException"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getMyNotifications_withCustomerToken_shouldReturn200() throws Exception {
        String token = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        NotificationDto dto = new NotificationDto();
        dto.setId(1L);
        dto.setUserId(10L);
        dto.setTitle("Order Placed");
        dto.setMessage("Your order was placed successfully");
        dto.setCreatedAt(LocalDateTime.now());

        when(notificationService.getMyNotifications(eq(10L))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getUserNotifications_withCustomerToken_shouldReturn403WithSafeMessage() throws Exception {
        String customerToken = generateToken("customer1", 10L, "CUSTOMER", 3600000);
        mockMvc.perform(get("/api/v1/notifications/user/99")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getUserNotifications_withAdminToken_shouldReturn200() throws Exception {
        String adminToken = generateToken("adminUser", 1L, "ADMIN", 3600000);
        NotificationDto dto = new NotificationDto();
        dto.setId(2L);
        dto.setUserId(99L);
        dto.setTitle("Password Reset");
        dto.setMessage("Password reset request");
        dto.setCreatedAt(LocalDateTime.now());

        when(notificationService.getUserNotifications(eq(99L))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications/user/99")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
