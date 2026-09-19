package com.eshoppingzone.wallet.audit.controller;

import com.eshoppingzone.wallet.audit.dto.AuditLogDto;
import com.eshoppingzone.wallet.audit.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Autowired
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
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
            @PageableDefault(size = 20, sort = "timestamp", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLogDto> results = auditLogService.searchAuditLogs(
                actorUserId, action, resourceType, resourceId, serviceName, outcome, correlationId, startDate, endDate, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> getAuditLogById(@PathVariable Long id) {
        return auditLogService.getAuditLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
