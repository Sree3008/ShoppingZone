package com.eshoppingzone.order.audit.event;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuditEvent implements Serializable {

    private String eventId;
    private LocalDateTime timestamp;
    private String actorUserId;
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
    private String idempotencyKey;
    private String metadata;

    public AuditEvent() {
    }

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
}
