package com.eshoppingzone.auth.audit;

import com.eshoppingzone.auth.audit.controller.AuditLogController;
import com.eshoppingzone.auth.audit.dto.AuditLogDto;
import com.eshoppingzone.auth.audit.entity.AuditLog;
import com.eshoppingzone.auth.audit.repository.AuditLogRepository;
import com.eshoppingzone.auth.audit.service.AuditLogService;
import com.eshoppingzone.auth.dto.AuthResponse;
import com.eshoppingzone.auth.dto.LoginRequest;
import com.eshoppingzone.auth.dto.RegisterRequest;
import com.eshoppingzone.auth.dto.UserDto;
import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.exception.InvalidCredentialsException;
import com.eshoppingzone.auth.repository.PasswordResetTokenRepository;
import com.eshoppingzone.auth.repository.RefreshTokenRepository;
import com.eshoppingzone.auth.repository.UserRepository;
import com.eshoppingzone.auth.security.JwtTokenProvider;
import com.eshoppingzone.auth.service.AuthServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAuditTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private AuthServiceImpl authService;
    private AuditLogController auditLogController;

    private User testUser;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper(), rabbitTemplate);
        authService = new AuthServiceImpl(userRepository, tokenRepository, refreshTokenRepository,
                passwordEncoder, jwtTokenProvider, rabbitTemplate, auditLogService);
        auditLogController = new AuditLogController(auditLogService);

        testUser = new User();
        testUser.setId(101L);
        testUser.setUsername("testcustomer");
        testUser.setEmail("test@customer.com");
        testUser.setPassword("encodedPassword");
        testUser.setRole(Role.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("1. Successful login creates audit event with action LOGIN_SUCCESS and outcome SUCCESS")
    void testSuccessfulLoginAudited() {
        when(userRepository.findByUsername("testcustomer")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token-xyz");
        when(jwtTokenProvider.generateRefreshToken(eq(testUser), anyString())).thenReturn("refresh-token-xyz");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(3600000L);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginRequest request = new LoginRequest("testcustomer", "rawPassword");
        AuthResponse response = authService.login(request);

        assertNotNull(response);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("LOGIN_SUCCESS", log.getAction());
        assertEquals("SUCCESS", log.getOutcome());
        assertEquals("AUTH_SERVICE", log.getServiceName());
        assertEquals("USER", log.getResourceType());
        assertEquals("101", log.getResourceId());
        assertEquals(101L, log.getActorUserId());
        assertEquals("testcustomer", log.getActorUsername());
        assertNotNull(log.getEventId());
        assertNotNull(log.getTimestamp());
        assertNotNull(log.getCorrelationId());
        // Verify secret password is NOT persisted
        assertNull(log.getFailureReason());
        assertFalse(log.getMetadata().contains("rawPassword"));
        assertFalse(log.getMetadata().contains("encodedPassword"));
    }

    @Test
    @DisplayName("2. Failed login creates audit event with action LOGIN_FAILED and outcome FAILURE")
    void testFailedLoginAudited() {
        when(userRepository.findByUsername("baduser")).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginRequest request = new LoginRequest("baduser", "wrongSecretPassword");
        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("LOGIN_FAILED", log.getAction());
        assertEquals("FAILURE", log.getOutcome());
        assertEquals("AUTH_SERVICE", log.getServiceName());
        assertEquals("baduser", log.getActorUsername());
        assertEquals("Invalid username or password", log.getFailureReason());
        // Verify password is NOT in metadata or failureReason
        assertFalse(log.getFailureReason().contains("wrongSecretPassword"));
        assertFalse(log.getMetadata().contains("wrongSecretPassword"));
    }

    @Test
    @DisplayName("3. User registration creates audit event with action USER_REGISTERED")
    void testUserRegistrationAudited() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@user.com")).thenReturn(false);
        when(passwordEncoder.encode("secretPassword123")).thenReturn("encodedSecret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(202L);
            return u;
        });
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest("newuser", "new@user.com", "secretPassword123", "New User", "1234567890");
        UserDto dto = authService.register(request);

        assertNotNull(dto);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertEquals("USER_REGISTERED", log.getAction());
        assertEquals("SUCCESS", log.getOutcome());
        assertEquals("202", log.getResourceId());
        assertEquals(202L, log.getActorUserId());
        assertFalse(log.getMetadata().contains("secretPassword123"));
    }

    @Test
    @DisplayName("4. Audit log entity immutability: @PreUpdate throws UnsupportedOperationException")
    void testAuditLogImmutabilityOnUpdate() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preUpdate);
    }

    @Test
    @DisplayName("5. Audit log entity immutability: @PreRemove throws UnsupportedOperationException")
    void testAuditLogImmutabilityOnDelete() {
        AuditLog auditLog = new AuditLog();
        assertThrows(UnsupportedOperationException.class, auditLog::preRemove);
    }

    @Test
    @DisplayName("6. Admin can query audit logs with database-level pagination and filtering")
    void testAdminQueryAuditLogs() {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventId("evt-123");
        auditLog.setAction("LOGIN_SUCCESS");
        auditLog.setOutcome("SUCCESS");
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setServiceName("AUTH_SERVICE");

        Page<AuditLog> pagedResult = new PageImpl<>(List.of(auditLog));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagedResult);

        ResponseEntity<?> response = auditLogController.searchAuditLogs(
                101L, "LOGIN_SUCCESS", "USER", "101", "AUTH_SERVICE", "SUCCESS", null, null, null, 0, 20, "timestamp", "desc"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("7. Audit log failure does NOT break business flow (non-blocking exception handling)")
    void testAuditFailureDoesNotBreakBusinessFlow() {
        when(userRepository.findByUsername("testcustomer")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access-token-xyz");
        when(jwtTokenProvider.generateRefreshToken(eq(testUser), anyString())).thenReturn("refresh-token-xyz");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(3600000L);
        // Simulate DB error when saving audit log
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database connection timeout"));

        LoginRequest request = new LoginRequest("testcustomer", "rawPassword");
        // Must succeed despite audit DB failure!
        AuthResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("testcustomer", response.getUsername());
    }

    @Test
    @DisplayName("8. Sensitive data masking: password and token keys in metadata are masked")
    void testSensitiveDataMaskingInMetadata() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditLogService.log(
                "TEST_ACTION", "TEST_RESOURCE", "1", "SUCCESS", null,
                Map.of("password", "superSecret123", "jwtToken", "eyJhbGciOi...", "amount", 50.0),
                101L, "admin", "ADMIN", null
        );

        assertNotNull(saved);
        assertNotNull(saved.getMetadata());
        assertFalse(saved.getMetadata().contains("superSecret123"));
        assertFalse(saved.getMetadata().contains("eyJhbGciOi..."));
        assertTrue(saved.getMetadata().contains("***MASKED***"));
        assertTrue(saved.getMetadata().contains("50.0"));
    }
}
