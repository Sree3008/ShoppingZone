package com.eshoppingzone.auth.audit.service;

import com.eshoppingzone.auth.audit.context.AuditContext;
import com.eshoppingzone.auth.audit.dto.AuditEvent;
import com.eshoppingzone.auth.audit.dto.AuditLogDto;
import com.eshoppingzone.auth.audit.entity.AuditLog;
import com.eshoppingzone.auth.audit.repository.AuditLogRepository;
import com.eshoppingzone.auth.audit.repository.AuditLogSpecification;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String SERVICE_NAME = "AUTH_SERVICE";
    private static final String AUDIT_EXCHANGE = "audit.exchange";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository,
                           ObjectMapper objectMapper,
                           @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(String action,
                        String resourceType,
                        String resourceId,
                        String outcome,
                        String failureReason,
                        Map<String, Object> metadata) {
        return log(action, resourceType, resourceId, outcome, failureReason, metadata, null, null, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(String action,
                        String resourceType,
                        String resourceId,
                        String outcome,
                        String failureReason,
                        Map<String, Object> metadata,
                        Long explicitActorUserId,
                        String explicitActorUsername,
                        String explicitActorRole,
                        String idempotencyKey) {
        try {
            AuditContext.AuditContextData context = AuditContext.get();

            Long actorUserId = explicitActorUserId != null ? explicitActorUserId : context.getActorUserId();
            String actorUsername = explicitActorUsername != null ? explicitActorUsername : context.getActorUsername();
            String actorRole = explicitActorRole != null ? explicitActorRole : context.getActorRole();

            // Extract from Spring Security Context if not explicitly set
            if (actorUsername == null || actorRole == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    if (actorUsername == null) {
                        actorUsername = auth.getName();
                    }
                    if (actorRole == null && !auth.getAuthorities().isEmpty()) {
                        actorRole = auth.getAuthorities().iterator().next().getAuthority();
                    }
                }
            }

            if (actorRole == null || actorRole.isBlank()) {
                actorRole = "ANONYMOUS";
            }
            if (actorUsername == null || actorUsername.isBlank()) {
                actorUsername = "anonymous";
            }

            String correlationId = context.getCorrelationId();
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            String serializedMetadata = sanitizeAndSerializeMetadata(metadata);

            AuditLog auditLog = new AuditLog();
            auditLog.setEventId(UUID.randomUUID().toString());
            auditLog.setTimestamp(LocalDateTime.now());
            auditLog.setActorUserId(actorUserId);
            auditLog.setActorUsername(actorUsername);
            auditLog.setActorRole(actorRole);
            auditLog.setServiceName(SERVICE_NAME);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setHttpMethod(context.getHttpMethod());
            auditLog.setEndpoint(context.getEndpoint());
            auditLog.setOutcome(outcome);
            auditLog.setFailureReason(failureReason);
            auditLog.setIpAddress(context.getIpAddress());
            auditLog.setUserAgent(context.getUserAgent());
            auditLog.setCorrelationId(correlationId);
            auditLog.setIdempotencyKeyReference(idempotencyKey);
            auditLog.setMetadata(serializedMetadata);
            auditLog.setCreatedAt(LocalDateTime.now());

            AuditLog saved = auditLogRepository.save(auditLog);

            // Asynchronously publish to RabbitMQ if template is present
            publishAuditEvent(saved);

            return saved;
        } catch (Exception e) {
            // Non-blocking: audit failure must NOT break the business transaction
            log.error("Failed to persist audit log for action: {} in service: {}. Error: {}",
                    action, SERVICE_NAME, e.getMessage(), e);
            return null;
        }
    }

    private void publishAuditEvent(AuditLog auditLog) {
        if (rabbitTemplate != null && auditLog != null) {
            try {
                AuditEvent event = new AuditEvent(
                        auditLog.getEventId(),
                        auditLog.getTimestamp(),
                        auditLog.getActorUserId(),
                        auditLog.getActorUsername(),
                        auditLog.getActorRole(),
                        auditLog.getServiceName(),
                        auditLog.getAction(),
                        auditLog.getResourceType(),
                        auditLog.getResourceId(),
                        auditLog.getOutcome(),
                        auditLog.getCorrelationId(),
                        auditLog.getMetadata()
                );
                String routingKey = "audit." + SERVICE_NAME.toLowerCase() + "." + auditLog.getAction().toLowerCase();
                rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, routingKey, event);
            } catch (Exception ex) {
                log.warn("Failed to publish audit event to RabbitMQ: {}", ex.getMessage());
            }
        }
    }

    private String sanitizeAndSerializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                sanitized.put(key, "***MASKED***");
            } else {
                sanitized.put(key, value);
            }
        }
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return String.valueOf(sanitized);
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("key")
                || lower.contains("cvv")
                || lower.contains("pin")
                || lower.contains("credential")
                || lower.contains("cardnumber");
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchAuditLogs(Long actorUserId,
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
                actorUserId, action, resourceType, resourceId, serviceName, outcome, correlationId, startDate, endDate
        );
        return auditLogRepository.findAll(spec, pageable).map(AuditLogDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLogDto> getAuditLogById(Long id) {
        return auditLogRepository.findById(id).map(AuditLogDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLogDto> getAuditLogByEventId(String eventId) {
        return auditLogRepository.findByEventId(eventId).map(AuditLogDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogsByActor(Long actorUserId, Pageable pageable) {
        return auditLogRepository.findByActorUserId(actorUserId, pageable).map(AuditLogDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogsByResource(String resourceType, String resourceId, Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable).map(AuditLogDto::fromEntity);
    }
}
