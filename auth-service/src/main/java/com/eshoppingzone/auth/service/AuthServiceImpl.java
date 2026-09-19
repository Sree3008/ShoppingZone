package com.eshoppingzone.auth.service;

import com.eshoppingzone.auth.config.RabbitMQConfig;
import com.eshoppingzone.auth.dto.*;
import com.eshoppingzone.auth.entity.PasswordResetToken;
import com.eshoppingzone.auth.entity.RefreshToken;
import com.eshoppingzone.auth.entity.Role;
import com.eshoppingzone.auth.entity.User;
import com.eshoppingzone.auth.entity.UserStatus;
import com.eshoppingzone.auth.exception.DuplicateResourceException;
import com.eshoppingzone.auth.exception.InvalidCredentialsException;
import com.eshoppingzone.auth.exception.InvalidTokenException;
import com.eshoppingzone.auth.exception.ResourceNotFoundException;
import com.eshoppingzone.auth.repository.PasswordResetTokenRepository;
import com.eshoppingzone.auth.repository.RefreshTokenRepository;
import com.eshoppingzone.auth.repository.UserRepository;
import com.eshoppingzone.auth.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    private com.eshoppingzone.auth.audit.service.AuditLogService auditLogService;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordResetTokenRepository tokenRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rabbitTemplate = rabbitTemplate;
    }

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordResetTokenRepository tokenRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RabbitTemplate rabbitTemplate,
                           com.eshoppingzone.auth.audit.service.AuditLogService auditLogService) {
        this(userRepository, tokenRepository, refreshTokenRepository, passwordEncoder,
                jwtTokenProvider, rabbitTemplate);
        this.auditLogService = auditLogService;
    }

    public void setAuditLogService(
            com.eshoppingzone.auth.audit.service.AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private void auditLog(String action, String resourceType, String resourceId, String outcome,
                          String failureReason, java.util.Map<String, Object> metadata,
                          Long actorUserId, String actorUsername, String actorRole) {
        if (auditLogService != null) {
            try {
                auditLogService.log(action, resourceType, resourceId, outcome, failureReason, metadata,
                        actorUserId, actorUsername, actorRole, null);
            } catch (Exception e) {
                log.warn("Failed to audit action {}: {}", action, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            auditLog("USER_REGISTRATION_FAILED", "USER", request.getUsername(), "FAILURE",
                    "Username already exists",
                    java.util.Map.of("username", request.getUsername()),
                    null, request.getUsername(), "ANONYMOUS");
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            auditLog("USER_REGISTRATION_FAILED", "USER", request.getEmail(), "FAILURE",
                    "Email already exists",
                    java.util.Map.of("email", request.getEmail()),
                    null, request.getUsername(), "ANONYMOUS");
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        auditLog("USER_REGISTERED", "USER", String.valueOf(savedUser.getId()), "SUCCESS", null,
                java.util.Map.of(
                        "username", savedUser.getUsername(),
                        "email", savedUser.getEmail(),
                        "role", savedUser.getRole().name()),
                savedUser.getId(), savedUser.getUsername(), savedUser.getRole().name());

        try {
            UserRegisteredEvent event = new UserRegisteredEvent(
                    savedUser.getId(),
                    savedUser.getUsername(),
                    savedUser.getEmail(),
                    savedUser.getFullName(),
                    savedUser.getRole()
            );

            CorrelationData correlationData =
                    new CorrelationData("user-registered-" + savedUser.getId());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                    event,
                    correlationData
            );

        } catch (Exception e) {
            log.warn("Failed to publish user registered event to RabbitMQ: {}",
                    e.getMessage());
        }

        return UserDto.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPassword())) {

            auditLog("LOGIN_FAILED", "USER", request.getUsername(), "FAILURE",
                    "Invalid username or password",
                    java.util.Map.of("username", request.getUsername()),
                    null, request.getUsername(), "ANONYMOUS");

            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            auditLog("LOGIN_FAILED", "USER", user.getUsername(), "FAILURE",
                    "Account blocked",
                    java.util.Map.of("username", user.getUsername()),
                    user.getId(), user.getUsername(), user.getRole().name());

            throw new InvalidCredentialsException(
                    "Account has been blocked. Please contact support.");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            auditLog("LOGIN_FAILED", "USER", user.getUsername(), "FAILURE",
                    "Account inactive",
                    java.util.Map.of("username", user.getUsername()),
                    user.getId(), user.getUsername(), user.getRole().name());

            throw new InvalidCredentialsException(
                    "Account is inactive. Please contact support.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);

        String refreshJti = UUID.randomUUID().toString();

        String refreshToken =
                jwtTokenProvider.generateRefreshToken(user, refreshJti);

        LocalDateTime expiryDate =
                LocalDateTime.now().plusSeconds(
                        jwtTokenProvider.getRefreshTokenExpirationMs() / 1000);

        RefreshToken refreshTokenEntity =
                new RefreshToken(refreshJti, user, expiryDate);

        refreshTokenRepository.save(refreshTokenEntity);

        auditLog("LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), "SUCCESS", null,
                java.util.Map.of(
                        "username", user.getUsername(),
                        "role", user.getRole().name()),
                user.getId(), user.getUsername(), user.getRole().name());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {

        String token = request.getRefreshToken();

        if (token == null || token.isBlank()) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Refresh token is blank",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException("Refresh token cannot be blank");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Invalid or expired refresh token",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException(
                    "Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(token);

        if (JwtTokenProvider.TOKEN_TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Access token used for refresh",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException(
                    "Access token cannot be used to refresh tokens. Please provide a valid refresh token.");
        }

        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Invalid token type for refresh",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException(
                    "Invalid token type. Expected a refresh token.");
        }

        String jti = jwtTokenProvider.getJti(token);

        if (jti == null || jti.isBlank()) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Missing JTI in refresh token",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException(
                    "Invalid refresh token: missing JTI");
        }

        RefreshToken tokenRecord =
                refreshTokenRepository.findByJti(jti).orElse(null);

        if (tokenRecord == null) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN", null, "FAILURE",
                    "Refresh token not found in store",
                    null, null, "anonymous", "ANONYMOUS");

            throw new InvalidTokenException(
                    "Refresh token not found or invalid");
        }

        if (tokenRecord.isRevoked()) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN",
                    String.valueOf(tokenRecord.getUser().getId()),
                    "FAILURE",
                    "Revoked refresh token reuse detected",
                    java.util.Map.of(
                            "username",
                            tokenRecord.getUser().getUsername()),
                    tokenRecord.getUser().getId(),
                    tokenRecord.getUser().getUsername(),
                    tokenRecord.getUser().getRole().name());

            throw new InvalidTokenException(
                    "Revoked refresh token reuse detected. Please log in again.");
        }

        if (tokenRecord.isExpired()) {
            auditLog("TOKEN_SECURITY_FAILURE", "TOKEN",
                    String.valueOf(tokenRecord.getUser().getId()),
                    "FAILURE",
                    "Refresh token expired",
                    java.util.Map.of(
                            "username",
                            tokenRecord.getUser().getUsername()),
                    tokenRecord.getUser().getId(),
                    tokenRecord.getUser().getUsername(),
                    tokenRecord.getUser().getRole().name());

            throw new InvalidTokenException(
                    "Refresh token has expired. Please log in again.");
        }

        User user = tokenRecord.getUser();

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new InvalidCredentialsException(
                    "Account has been blocked. Please contact support.");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InvalidCredentialsException(
                    "Account is inactive. Please contact support.");
        }

        String newRefreshJti = UUID.randomUUID().toString();

        tokenRecord.setRevoked(true);
        tokenRecord.setRevokedAt(LocalDateTime.now());
        tokenRecord.setReplacedByJti(newRefreshJti);

        refreshTokenRepository.save(tokenRecord);

        String newAccessToken =
                jwtTokenProvider.generateAccessToken(user);

        String newRefreshToken =
                jwtTokenProvider.generateRefreshToken(user, newRefreshJti);

        LocalDateTime newExpiryDate =
                LocalDateTime.now().plusSeconds(
                        jwtTokenProvider.getRefreshTokenExpirationMs() / 1000);

        RefreshToken newRecord =
                new RefreshToken(newRefreshJti, user, newExpiryDate);

        refreshTokenRepository.save(newRecord);

        auditLog("TOKEN_REFRESH_SUCCESS", "TOKEN",
                String.valueOf(user.getId()), "SUCCESS", null,
                java.util.Map.of("username", user.getUsername()),
                user.getId(), user.getUsername(), user.getRole().name());

        return new TokenRefreshResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {

        String token = request.getRefreshToken();

        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(
                    "Refresh token is required for logout");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            throw new InvalidTokenException(
                    "Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(token);

        if (JwtTokenProvider.TOKEN_TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException(
                    "Invalid token type for logout. Expected a refresh token.");
        }

        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException(
                    "Invalid token type for logout.");
        }

        String jti = jwtTokenProvider.getJti(token);

        if (jti != null && !jti.isBlank()) {

            refreshTokenRepository.findByJti(jti).ifPresent(record -> {

                if (!record.isRevoked()) {

                    record.setRevoked(true);
                    record.setRevokedAt(LocalDateTime.now());

                    refreshTokenRepository.save(record);

                    auditLog(
                            "LOGOUT",
                            "USER",
                            String.valueOf(record.getUser().getId()),
                            "SUCCESS",
                            null,
                            java.util.Map.of(
                                    "username",
                                    record.getUser().getUsername()),
                            record.getUser().getId(),
                            record.getUser().getUsername(),
                            record.getUser().getRole().name()
                    );
                }
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with username: " + username));

        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with email: " + request.getEmail()));

        tokenRepository.findByUserAndUsedFalse(user).ifPresent(t -> {
            t.setUsed(true);
            tokenRepository.save(t);
        });

        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken tokenEntity =
                new PasswordResetToken(
                        resetToken,
                        user,
                        LocalDateTime.now().plusHours(1)
                );

        tokenRepository.save(tokenEntity);

        auditLog(
                "PASSWORD_RESET_REQUESTED",
                "USER",
                String.valueOf(user.getId()),
                "SUCCESS",
                null,
                java.util.Map.of(
                        "email", user.getEmail(),
                        "username", user.getUsername()),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );

        log.info("==================================================");
        log.info("          PASSWORD RESET REQUEST");
        log.info("==================================================");
        log.info("Username    : {}", user.getUsername());
        log.info("Email       : {}", user.getEmail());
        log.info("Reset Token : {}", resetToken);
        log.info("Expires At  : {}", tokenEntity.getExpiryDate());
        log.info("==================================================");
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        PasswordResetToken resetToken =
                tokenRepository.findByToken(request.getToken())
                        .orElse(null);

        if (resetToken == null) {

            auditLog(
                    "PASSWORD_RESET_FAILED",
                    "USER",
                    null,
                    "FAILURE",
                    "Invalid password reset token",
                    null,
                    null,
                    "anonymous",
                    "ANONYMOUS"
            );

            throw new InvalidTokenException(
                    "Invalid password reset token");
        }

        if (resetToken.isUsed()) {

            auditLog(
                    "PASSWORD_RESET_FAILED",
                    "USER",
                    String.valueOf(resetToken.getUser().getId()),
                    "FAILURE",
                    "Password reset token has already been used",
                    null,
                    resetToken.getUser().getId(),
                    resetToken.getUser().getUsername(),
                    resetToken.getUser().getRole().name()
            );

            throw new InvalidTokenException(
                    "Password reset token has already been used");
        }

        if (resetToken.isExpired()) {

            auditLog(
                    "PASSWORD_RESET_FAILED",
                    "USER",
                    String.valueOf(resetToken.getUser().getId()),
                    "FAILURE",
                    "Password reset token has expired",
                    null,
                    resetToken.getUser().getId(),
                    resetToken.getUser().getUsername(),
                    resetToken.getUser().getRole().name()
            );

            throw new InvalidTokenException(
                    "Password reset token has expired");
        }

        User user = resetToken.getUser();

        user.setPassword(
                passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);

        resetToken.setUsed(true);

        tokenRepository.save(resetToken);

        auditLog(
                "PASSWORD_RESET_SUCCESS",
                "USER",
                String.valueOf(user.getId()),
                "SUCCESS",
                null,
                java.util.Map.of("username", user.getUsername()),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );

        System.out.println(
                "[AUTH-SERVICE] Password successfully reset for user: "
                        + user.getUsername());
    }

    @Override
    @Transactional
    public UserDto createUserByAdmin(CreateUserRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Username already exists: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email already exists: " + request.getEmail());
        }

        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(
                passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        auditLog(
                "ADMIN_USER_CREATED",
                "USER",
                String.valueOf(savedUser.getId()),
                "SUCCESS",
                null,
                java.util.Map.of(
                        "username", savedUser.getUsername(),
                        "role", savedUser.getRole().name()),
                null,
                null,
                "ADMIN"
        );

        return UserDto.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers(
            Role role,
            UserStatus status) {

        List<User> users;

        if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status);

        } else if (role != null) {
            users = userRepository.findByRole(role);

        } else if (status != null) {
            users = userRepository.findByStatus(status);

        } else {
            users = userRepository.findAll();
        }

        return users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public UserDto updateUserStatus(
            Long id,
            UserStatus status) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id));

        user.setStatus(status);

        User saved = userRepository.save(user);

        return UserDto.fromEntity(saved);
    }
}