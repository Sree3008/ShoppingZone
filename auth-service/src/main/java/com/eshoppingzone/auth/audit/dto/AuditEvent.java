package com.eshoppingzone.auth.audit.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuditEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private LocalDateTime timestamp;
    private Long actorUserId;
    private String actorUsername;
    private String actorRole;
    private String serviceName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String correlationId;
    private String metadata;

    public AuditEvent() {
    }

    public AuditEvent(String eventId, LocalDateTime timestamp, Long actorUserId, String actorUsername,
                      String actorRole, String serviceName, String action, String resourceType,
                      String resourceId, String outcome, String correlationId, String metadata) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.serviceName = serviceName;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.correlationId = correlationId;
        this.metadata = metadata;
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

    public String getOutcome() {
        return outcome;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getMetadata() {
        return metadata;
    }
}
