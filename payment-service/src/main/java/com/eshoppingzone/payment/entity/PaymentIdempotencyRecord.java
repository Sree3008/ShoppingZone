package com.eshoppingzone.payment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = {"idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_pay_idem_user", columnList = "user_id, idempotency_key"),
                @Index(name = "idx_pay_idem_created", columnList = "created_at")
        }
)
public class PaymentIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentIdempotencyRecord() {
    }

    public PaymentIdempotencyRecord(String idempotencyKey, String operation, Long userId, String requestFingerprint, IdempotencyStatus status) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.userId = userId;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyStatus status) {
        this.status = status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
