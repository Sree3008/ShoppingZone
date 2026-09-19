package com.eshoppingzone.product.audit.controller;

import com.eshoppingzone.product.audit.dto.AuditLogDto;
import com.eshoppingzone.product.audit.service.AuditLogService;
import com.eshoppingzone.product.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping({"/api/v1/products/audit-logs", "/api/v1/admin/products/audit-logs", "/api/v1/audit-logs"})
@Tag(name = "Product Audit Log Management", description = "Admin-only APIs for querying product audit trails")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(summary = "Search Audit Logs", description = "Admin-only paginated search with database-level multi-attribute filtering")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> searchAuditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(dir, sortBy));

        Page<AuditLogDto> result = auditLogService.searchAuditLogs(
                actorUserId, action, resourceType, resourceId, serviceName, outcome, correlationId, startDate, endDate, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Audit Log by ID", description = "Admin-only retrieval of a single audit log record by primary key")
    public ResponseEntity<ApiResponse<AuditLogDto>> getById(@PathVariable Long id) {
        return auditLogService.getAuditLogById(id)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Audit log found", dto)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Get Audit Log by Event ID", description = "Admin-only retrieval of audit log record by unique UUID eventId")
    public ResponseEntity<ApiResponse<AuditLogDto>> getByEventId(@PathVariable String eventId) {
        return auditLogService.getAuditLogByEventId(eventId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Audit log found", dto)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get Audit Logs by Actor User ID", description = "Admin-only retrieval of audit trails for a specific user")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogDto> result = auditLogService.getAuditLogsByActor(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("User audit logs retrieved", result));
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    @Operation(summary = "Get Audit Logs by Resource", description = "Admin-only retrieval of audit trail for a specific resource")
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogDto> result = auditLogService.getAuditLogsByResource(resourceType, resourceId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Resource audit logs retrieved", result));
    }
}
