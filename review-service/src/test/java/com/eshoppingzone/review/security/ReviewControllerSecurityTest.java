package com.eshoppingzone.review.security;

import com.eshoppingzone.review.controller.AdminReviewController;
import com.eshoppingzone.review.controller.ReviewController;
import com.eshoppingzone.review.dto.*;
import com.eshoppingzone.review.enums.ReviewStatus;
import com.eshoppingzone.review.exception.GlobalExceptionHandler;
import com.eshoppingzone.review.service.ReviewService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.TestPropertySource;

@WebMvcTest(controllers = {ReviewController.class, AdminReviewController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
class ReviewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

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

    // 1. Unauthenticated create -> 401
    @Test
    @DisplayName("1. Unauthenticated review creation is rejected with 401")
    void testUnauthenticatedCreateRejected() throws Exception {
        CreateReviewRequest request = new CreateReviewRequest(500L, 10L, 100L, 5, "Good", "Valid review comment");

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 2. CUSTOMER can create own review
    @Test
    @DisplayName("2. CUSTOMER can create review successfully with 201")
    void testCustomerCanCreateOwnReview() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");
        CreateReviewRequest request = new CreateReviewRequest(500L, 10L, 100L, 5, "Good", "Valid review comment");

        ReviewDto dto = new ReviewDto(1L, 50L, "customer1", 500L, 10L, 100L, 5, "Good", "Valid review comment", ReviewStatus.PENDING, true, LocalDateTime.now(), LocalDateTime.now());
        when(reviewService.createReview(eq(50L), eq("customer1"), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // 3. CUSTOMER cannot update another customer's review -> 403
    @Test
    @DisplayName("3. CUSTOMER cannot update another customer's review (403 Forbidden)")
    void testCustomerCannotUpdateAnotherCustomersReview() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");
        UpdateReviewRequest request = new UpdateReviewRequest(4, "New Title", "New comment text");

        doThrow(new AccessDeniedException("You can only edit your own reviews"))
                .when(reviewService).updateReview(eq(99L), eq(50L), any());

        mockMvc.perform(put("/api/v1/reviews/99")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 4. CUSTOMER cannot delete another customer's review -> 403
    @Test
    @DisplayName("4. CUSTOMER cannot delete another customer's review (403 Forbidden)")
    void testCustomerCannotDeleteAnotherCustomersReview() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        doThrow(new AccessDeniedException("You can only delete your own reviews"))
                .when(reviewService).deleteReview(eq(99L), eq(50L), eq(false));

        mockMvc.perform(delete("/api/v1/reviews/99")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 5. CUSTOMER cannot access admin APIs -> 403
    @Test
    @DisplayName("5. CUSTOMER cannot access admin moderation APIs (403 Forbidden)")
    void testCustomerCannotAccessAdminApis() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        mockMvc.perform(get("/api/v1/admin/reviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/reviews/1/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // 6. ADMIN can access moderation APIs
    @Test
    @DisplayName("6. ADMIN can access moderation APIs successfully")
    void testAdminCanAccessModerationApis() throws Exception {
        String token = generateToken(1L, "admin", "ADMIN");

        when(reviewService.getAllReviews(any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/admin/reviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ReviewDto dto = new ReviewDto(1L, 50L, "customer1", 500L, 10L, 100L, 5, "Good", "Comment", ReviewStatus.APPROVED, true, LocalDateTime.now(), LocalDateTime.now());
        when(reviewService.approveReview(1L)).thenReturn(dto);

        mockMvc.perform(put("/api/v1/admin/reviews/1/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    // 7. Internal API cannot be accessed anonymously
    @Test
    @DisplayName("7. Admin API cannot be accessed anonymously (401 Unauthorized)")
    void testAnonymousAccessToAdminApiRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews"))
                .andExpect(status().isUnauthorized());
    }

    // 8. Invalid JWT rejected
    @Test
    @DisplayName("8. Invalid JWT is rejected with 401 Unauthorized")
    void testInvalidJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/my")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // 9. Missing JWT rejected
    @Test
    @DisplayName("9. Missing JWT is rejected with 401 Unauthorized on protected endpoint")
    void testMissingJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/my"))
                .andExpect(status().isUnauthorized());
    }

    // 10. Customer cannot modify customerId/status/verifiedPurchase through request payload
    @Test
    @DisplayName("10. Customer cannot inject customerId or status through request payload")
    void testCustomerCannotInjectIdentityThroughPayload() throws Exception {
        String token = generateToken(50L, "customer1", "CUSTOMER");

        // Payload tries to inject customerId=999, status=APPROVED, verifiedPurchase=false
        String maliciousJson = "{\"productId\":500,\"orderId\":10,\"orderItemId\":100,\"rating\":5,\"title\":\"Test\",\"comment\":\"Valid comment\",\"customerId\":999,\"status\":\"APPROVED\",\"verifiedPurchase\":false}";

        ReviewDto dto = new ReviewDto(1L, 50L, "customer1", 500L, 10L, 100L, 5, "Test", "Valid comment", ReviewStatus.PENDING, true, LocalDateTime.now(), LocalDateTime.now());
        when(reviewService.createReview(eq(50L), eq("customer1"), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(50L)) // Uses 50L from JWT, NOT 999!
                .andExpect(jsonPath("$.data.status").value("PENDING")); // Uses PENDING, NOT APPROVED!

        // Verify service was called with authenticated user ID 50L
        verify(reviewService).createReview(eq(50L), eq("customer1"), any());
    }
}
