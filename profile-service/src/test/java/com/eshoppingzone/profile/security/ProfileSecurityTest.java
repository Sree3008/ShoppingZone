package com.eshoppingzone.profile.security;

import com.eshoppingzone.profile.controller.ProfileController;
import com.eshoppingzone.profile.dto.UserProfileDto;
import com.eshoppingzone.profile.service.ProfileService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = ProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class ProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    private static final String JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Test
    void getMyProfile_withoutAuthentication_shouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unauthorized")));
    }

    @Test
    void getMyProfile_withInvalidToken_shouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/me")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unauthorized")));
    }

    @Test
    void getMyProfile_withValidToken_shouldReturn200Ok() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String validToken = Jwts.builder()
                .subject("customer1")
                .claims(Map.of("userId", 4L, "email", "customer1@eshoppingzone.com", "role", "CUSTOMER", "type", "ACCESS"))
                .signWith(key)
                .compact();

        UserProfileDto mockProfile = new UserProfileDto();
        mockProfile.setId(4L);
        mockProfile.setUserId(4L);
        mockProfile.setUsername("customer1");
        mockProfile.setEmail("customer1@eshoppingzone.com");
        mockProfile.setFullName("John Customer");

        when(profileService.getMyProfile(eq(4L), eq("customer1"), eq("customer1@eshoppingzone.com"))).thenReturn(mockProfile);

        mockMvc.perform(get("/api/v1/profiles/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(4L))
                .andExpect(jsonPath("$.data.username").value("customer1"));
    }

    @Test
    void getProfileByUserId_withoutAuthentication_shouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/user/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getProfileByUserId_asDifferentCustomer_shouldReturn403Forbidden() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String customerToken = Jwts.builder()
                .subject("customer1")
                .claims(Map.of("userId", 4L, "email", "customer1@eshoppingzone.com", "role", "CUSTOMER", "type", "ACCESS"))
                .signWith(key)
                .compact();

        // Customer 4 tries to access user 5's profile -> IDOR blocked
        mockMvc.perform(get("/api/v1/profiles/user/5")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access denied")));
    }

    @Test
    void getProfileByUserId_asOwnerCustomer_shouldReturn200Ok() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String customerToken = Jwts.builder()
                .subject("customer1")
                .claims(Map.of("userId", 4L, "email", "customer1@eshoppingzone.com", "role", "CUSTOMER", "type", "ACCESS"))
                .signWith(key)
                .compact();

        UserProfileDto mockProfile = new UserProfileDto();
        mockProfile.setId(4L);
        mockProfile.setUserId(4L);
        mockProfile.setUsername("customer1");
        mockProfile.setEmail("customer1@eshoppingzone.com");
        mockProfile.setFullName("John Customer");

        when(profileService.getProfileByUserId(4L)).thenReturn(mockProfile);

        // Customer 4 accesses own profile -> Allowed
        mockMvc.perform(get("/api/v1/profiles/user/4")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(4L));
    }

    @Test
    void getProfileByUserId_asInternal_shouldReturn200Ok() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String internalToken = Jwts.builder()
                .subject("internal-service")
                .claims(Map.of("userId", 0L, "email", "internal@eshoppingzone.com", "role", "INTERNAL", "type", "ACCESS"))
                .signWith(key)
                .compact();

        UserProfileDto mockProfile = new UserProfileDto();
        mockProfile.setId(4L);
        mockProfile.setUserId(4L);
        mockProfile.setUsername("customer1");

        when(profileService.getProfileByUserId(4L)).thenReturn(mockProfile);

        // Internal service accesses any profile -> Allowed
        mockMvc.perform(get("/api/v1/profiles/user/4")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(4L));
    }

    @Test
    void getAddressInternal_withoutAuthentication_shouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/addresses/1/internal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAddressInternal_asCustomer_shouldReturn403Forbidden() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String customerToken = Jwts.builder()
                .subject("customer1")
                .claims(Map.of("userId", 4L, "email", "customer1@eshoppingzone.com", "role", "CUSTOMER", "type", "ACCESS"))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/profiles/addresses/1/internal")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAddressInternal_asInternal_shouldReturn200Ok() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        String internalToken = Jwts.builder()
                .subject("order-service")
                .claims(Map.of("userId", 0L, "email", "order-service@eshoppingzone.com", "role", "INTERNAL", "type", "ACCESS"))
                .signWith(key)
                .compact();

        com.eshoppingzone.profile.dto.AddressDto mockAddr = new com.eshoppingzone.profile.dto.AddressDto();
        mockAddr.setId(1L);
        mockAddr.setStreet("123 Main St");

        when(profileService.getAddressInternal(1L)).thenReturn(mockAddr);

        mockMvc.perform(get("/api/v1/profiles/addresses/1/internal")
                        .header("Authorization", "Bearer " + internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L));
    }
}
