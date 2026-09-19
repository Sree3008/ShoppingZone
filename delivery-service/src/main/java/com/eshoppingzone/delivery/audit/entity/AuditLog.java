package com.eshoppingzone.delivery.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_delivery_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_delivery_audit_actor_user_id", columnList = "actorUserId"),
        @Index(name = "idx_delivery_audit_action", columnList = "action"),
        @Index(name = "idx_delivery_audit_resource", columnList = "resourceType, resourceId"),
        @Index(name = "idx_delivery_audit_correlation_id", columnList = "correlationId"),
        @Index(name = "idx_delivery_audit_service_name", columnList = "serviceName")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 64)
    private String actorUserId;

    @Column(length = 128)
    private String actorUsername;

    @Column(length = 64)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String serviceName;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 64)
    private String resourceType;

    @Column(length = 128)
    private String resourceId;

    @Column(length = 16)
    private String httpMethod;

    @Column(length = 256)
    private String endpoint;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(length = 512)
    private String failureReason;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 256)
    private String userAgent;

    @Column(length = 64)
    private String correlationId;

    @Column(length = 64)
    private String idempotencyKey;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        throw new UnsupportedOperationException("AuditLog records are immutable and cannot be updated");
    }

    @PreRemove
    public void preRemove() {
        throw new UnsupportedOperationException("AuditLog records are immutable and cannot be deleted");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
