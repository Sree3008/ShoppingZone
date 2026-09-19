package com.eshoppingzone.payment.audit.controller;

import com.eshoppingzone.payment.audit.dto.AuditLogDto;
import com.eshoppingzone.payment.audit.service.AuditLogService;
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
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam(required = false) String actorUserId,
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

        int clampedSize = Math.min(Math.max(size, 1), 100);
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampedSize, Sort.by(sortDirection, sortBy));

        Page<AuditLogDto> results = auditLogService.searchAuditLogs(
                actorUserId, action, resourceType, resourceId, serviceName, outcome, correlationId, startDate, endDate, pageable);

        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditLogDto> getAuditLogById(@PathVariable Long id) {
        return auditLogService.getAuditLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
