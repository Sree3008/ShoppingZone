package com.eshoppingzone.delivery.security;

import com.eshoppingzone.delivery.controller.AdminDeliveryController;
import com.eshoppingzone.delivery.controller.DeliveryController;
import com.eshoppingzone.delivery.dto.DeliveryDto;
import com.eshoppingzone.delivery.entity.DeliveryStatus;
import com.eshoppingzone.delivery.service.DeliveryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.eshoppingzone.delivery.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeliveryController.class, AdminDeliveryController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
})
class DeliverySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String generateToken(Long userId, String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("userId", userId, "email", username + "@test.com", "role", role, "type", "ACCESS"))
                .signWith(key)
                .compact();
    }

    @Test
    void getDeliveryByOrderId_withoutAuth_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries/order/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getDeliveryByOrderId_withInvalidToken_shouldReturn401WithSafeMessage() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries/order/100")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Jwt"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void getDeliveryByOrderId_asDifferentCustomer_shouldReturn403Forbidden() throws Exception {
        String token = generateToken(10L, "customer10", "CUSTOMER");

        DeliveryDto dto = new DeliveryDto();
        dto.setOrderId(100L);
        dto.setCustomerId(999L); // Different customer
        dto.setStatus("ASSIGNED");

        when(deliveryService.getDeliveryByOrderId(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/deliveries/order/100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access denied")));
    }

    @Test
    void getDeliveryByOrderId_asOwnerCustomer_shouldReturn200() throws Exception {
        String token = generateToken(10L, "customer10", "CUSTOMER");

        DeliveryDto dto = new DeliveryDto();
        dto.setOrderId(100L);
        dto.setCustomerId(10L); // Matching customer
        dto.setStatus("ASSIGNED");

        when(deliveryService.getDeliveryByOrderId(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/deliveries/order/100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(100L));
    }

    @Test
    void getDeliveryByOrderId_asAgent_shouldReturn200() throws Exception {
        String token = generateToken(2L, "agent1", "DELIVERY_AGENT");

        DeliveryDto dto = new DeliveryDto();
        dto.setOrderId(100L);
        dto.setCustomerId(999L);
        dto.setStatus("OUT_FOR_DELIVERY");

        when(deliveryService.getDeliveryByOrderId(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/deliveries/order/100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignDelivery_asCustomer_shouldReturn403() throws Exception {
        String token = generateToken(10L, "customer10", "CUSTOMER");

        mockMvc.perform(post("/api/v1/admin/deliveries/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"orderId\":100,\"agentId\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
