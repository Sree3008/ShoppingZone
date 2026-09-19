package com.eshoppingzone.notification.exception;

import com.eshoppingzone.notification.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.notification.audit.service.AuditLogService auditLogService;

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
        if (auditLogService != null) {
            try {
                java.util.Map<String, Object> meta = new java.util.HashMap<>();
                if (request != null && request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                if (request != null && request.getMethod() != null) meta.put("method", request.getMethod());
                auditLogService.log("FORBIDDEN_ACCESS", "SECURITY",
                        request != null && request.getRequestURI() != null ? request.getRequestURI() : "SECURITY",
                        "DENIED", "Access denied / security violation: " + ex.getMessage(), meta);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        log.error("Unhandled server error in notification-service: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
