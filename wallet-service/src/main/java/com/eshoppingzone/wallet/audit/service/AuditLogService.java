package com.eshoppingzone.wallet.audit.service;

import com.eshoppingzone.wallet.audit.context.AuditContext;
import com.eshoppingzone.wallet.audit.dto.AuditLogDto;
import com.eshoppingzone.wallet.audit.entity.AuditLog;
import com.eshoppingzone.wallet.audit.event.AuditEvent;
import com.eshoppingzone.wallet.audit.repository.AuditLogRepository;
import com.eshoppingzone.wallet.audit.specification.AuditLogSpecification;
import com.eshoppingzone.wallet.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String SERVICE_NAME = "wallet-service";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "token", "accesstoken", "refreshtoken", "secret", "cvv", "pin", "cardnumber", "authorization"
    ));

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper) {
        this(auditLogRepository, objectMapper, null);
    }

    public AuditLog logAction(String action, String resourceType, String resourceId, String outcome, String failureReason, Map<String, Object> metadata) {
        return log(action, resourceType, resourceId, outcome, failureReason, metadata, null, null, null, null);
    }

    public AuditLog log(String action, String resourceType, String resourceId, String outcome, String failureReason, Map<String, Object> metadata) {
        return log(action, resourceType, resourceId, outcome, failureReason, metadata, null, null, null, null);
    }

    public AuditLog logAction(String action, String resourceType, String resourceId, String outcome, String failureReason, Map<String, Object> metadata, String actorUserId, String actorUsername, String actorRole) {
        return log(action, resourceType, resourceId, outcome, failureReason, metadata, actorUserId, actorUsername, actorRole, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(String action,
                        String resourceType,
                        String resourceId,
                        String outcome,
                        String failureReason,
                        Map<String, Object> metadata,
                        String actorUserId,
                        String actorUsername,
                        String actorRole,
                        String idempotencyKey) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventId(UUID.randomUUID().toString());
            auditLog.setTimestamp(LocalDateTime.now());
            auditLog.setServiceName(SERVICE_NAME);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType != null ? resourceType : "UNKNOWN");
            auditLog.setResourceId(resourceId);
            auditLog.setOutcome(outcome != null ? outcome : "UNKNOWN");
            auditLog.setFailureReason(failureReason);

            if (actorUserId != null || actorRole != null) {
                auditLog.setActorUserId(actorUserId != null ? actorUserId : "SYSTEM");
                auditLog.setActorUsername(actorUsername != null ? actorUsername : "system");
                auditLog.setActorRole(actorRole != null ? actorRole : "ROLE_SYSTEM");
            } else {
                populateActorFromSecurityContext(auditLog);
            }

            auditLog.setCorrelationId(AuditContext.getCorrelationId());
            auditLog.setIpAddress(AuditContext.getClientIp());
            auditLog.setUserAgent(AuditContext.getUserAgent());
            auditLog.setHttpMethod(AuditContext.getHttpMethod());
            auditLog.setEndpoint(AuditContext.getRequestUri());
            auditLog.setIdempotencyKey(idempotencyKey);

            if (metadata != null && !metadata.isEmpty()) {
                Map<String, Object> sanitized = maskSensitiveFields(metadata);
                auditLog.setMetadata(objectMapper.writeValueAsString(sanitized));
            }

            AuditLog saved = auditLogRepository.save(auditLog);

            publishAuditEventSafely(saved);

            return saved;
        } catch (Exception e) {
            log.error("Failed to persist audit log for action {} on resource {}: {}", action, resourceType, e.getMessage());
            return null;
        }
    }

    private void populateActorFromSecurityContext(AuditLog auditLog) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal up) {
                auditLog.setActorUserId(String.valueOf(up.getUserId()));
                auditLog.setActorUsername(up.getUsername());
                auditLog.setActorRole(up.getRole());
            } else {
                auditLog.setActorUserId(auth.getName());
                auditLog.setActorUsername(auth.getName());
                auditLog.setActorRole(auth.getAuthorities().toString());
            }
        } else {
            auditLog.setActorUserId("ANONYMOUS");
            auditLog.setActorUsername("anonymous");
            auditLog.setActorRole("ANONYMOUS");
        }
    }

    private Map<String, Object> maskSensitiveFields(Map<String, Object> metadata) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase().replaceAll("[^a-z]", ""))) {
                copy.put(key, "[PROTECTED]");
            } else {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private void publishAuditEventSafely(AuditLog log) {
        if (rabbitTemplate != null) {
            try {
                AuditEvent event = new AuditEvent();
                event.setEventId(log.getEventId());
                event.setTimestamp(log.getTimestamp());
                event.setActorUserId(log.getActorUserId());
                event.setActorUsername(log.getActorUsername());
                event.setActorRole(log.getActorRole());
                event.setServiceName(log.getServiceName());
                event.setAction(log.getAction());
                event.setResourceType(log.getResourceType());
                event.setResourceId(log.getResourceId());
                event.setHttpMethod(log.getHttpMethod());
                event.setEndpoint(log.getEndpoint());
                event.setOutcome(log.getOutcome());
                event.setFailureReason(log.getFailureReason());
                event.setIpAddress(log.getIpAddress());
                event.setUserAgent(log.getUserAgent());
                event.setCorrelationId(log.getCorrelationId());
                event.setIdempotencyKey(log.getIdempotencyKey());
                event.setMetadata(log.getMetadata());

                rabbitTemplate.convertAndSend("audit.exchange", "audit." + SERVICE_NAME, event);
            } catch (Exception e) {
                // Non-blocking
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchAuditLogs(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String serviceName,
            String outcome,
            String correlationId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<AuditLog> spec = AuditLogSpecification.filter(
                actorUserId, action, resourceType, resourceId, serviceName, outcome, correlationId, startDate, endDate);

        return auditLogRepository.findAll(spec, pageable).map(AuditLogDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLogDto> getAuditLogById(Long id) {
        return auditLogRepository.findById(id).map(AuditLogDto::fromEntity);
    }
}
