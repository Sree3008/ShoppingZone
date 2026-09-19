package com.eshoppingzone.auth.audit.dto;

import com.eshoppingzone.auth.audit.entity.AuditLog;
import java.time.LocalDateTime;

public class AuditLogDto {
    private Long id;
    private String eventId;
    private LocalDateTime timestamp;
    private Long actorUserId;
    private String actorUsername;
    private String actorRole;
    private String serviceName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String httpMethod;
    private String endpoint;
    private String outcome;
    private String failureReason;
    private String ipAddress;
    private String userAgent;
    private String correlationId;
    private String idempotencyKeyReference;
    private String metadata;
    private LocalDateTime createdAt;

    public AuditLogDto() {
    }

    public static AuditLogDto fromEntity(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        AuditLogDto dto = new AuditLogDto();
        dto.id = entity.getId();
        dto.eventId = entity.getEventId();
        dto.timestamp = entity.getTimestamp();
        dto.actorUserId = entity.getActorUserId();
        dto.actorUsername = entity.getActorUsername();
        dto.actorRole = entity.getActorRole();
        dto.serviceName = entity.getServiceName();
        dto.action = entity.getAction();
        dto.resourceType = entity.getResourceType();
        dto.resourceId = entity.getResourceId();
        dto.httpMethod = entity.getHttpMethod();
        dto.endpoint = entity.getEndpoint();
        dto.outcome = entity.getOutcome();
        dto.failureReason = entity.getFailureReason();
        dto.ipAddress = entity.getIpAddress();
        dto.userAgent = entity.getUserAgent();
        dto.correlationId = entity.getCorrelationId();
        dto.idempotencyKeyReference = entity.getIdempotencyKeyReference();
        dto.metadata = entity.getMetadata();
        dto.createdAt = entity.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIdempotencyKeyReference() {
        return idempotencyKeyReference;
    }

    public String getMetadata() {
        return metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
