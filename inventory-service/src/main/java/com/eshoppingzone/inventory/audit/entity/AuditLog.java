package com.eshoppingzone.inventory.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_inventory_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_inventory_audit_actor_user_id", columnList = "actor_user_id"),
        @Index(name = "idx_inventory_audit_action", columnList = "action"),
        @Index(name = "idx_inventory_audit_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_inventory_audit_correlation_id", columnList = "correlation_id"),
        @Index(name = "idx_inventory_audit_service_name", columnList = "service_name")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 64)
    private String eventId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_username", updatable = false, length = 100)
    private String actorUsername;

    @Column(name = "actor_role", updatable = false, length = 50)
    private String actorRole;

    @Column(name = "service_name", nullable = false, updatable = false, length = 50)
    private String serviceName;

    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "resource_type", updatable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 100)
    private String resourceId;

    @Column(name = "http_method", updatable = false, length = 10)
    private String httpMethod;

    @Column(name = "endpoint", updatable = false, length = 255)
    private String endpoint;

    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private String outcome;

    @Column(name = "failure_reason", updatable = false, length = 500)
    private String failureReason;

    @Column(name = "ip_address", updatable = false, length = 50)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 255)
    private String userAgent;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key_ref", updatable = false, length = 128)
    private String idempotencyKeyReference;

    @Column(name = "metadata", updatable = false, columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    public void prePersist() {
        if (this.eventId == null) {
            this.eventId = UUID.randomUUID().toString();
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        throw new UnsupportedOperationException("Audit logs are immutable and cannot be updated");
    }

    @PreRemove
    public void preRemove() {
        throw new UnsupportedOperationException("Audit logs are immutable and cannot be deleted");
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getIdempotencyKeyReference() {
        return idempotencyKeyReference;
    }

    public void setIdempotencyKeyReference(String idempotencyKeyReference) {
        this.idempotencyKeyReference = idempotencyKeyReference;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
