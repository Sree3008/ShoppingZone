package com.eshoppingzone.product.exception;

import com.eshoppingzone.product.dto.ApiResponse;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.product.audit.service.AuditLogService auditLogService;

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(UnauthorizedException ex, jakarta.servlet.http.HttpServletRequest request) {
        if (auditLogService != null) {
            try {
                java.util.Map<String, Object> meta = new java.util.HashMap<>();
                if (request != null && request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                if (request != null && request.getMethod() != null) meta.put("method", request.getMethod());
                auditLogService.log("FORBIDDEN_ACCESS", "SECURITY",
                        request != null && request.getRequestURI() != null ? request.getRequestURI() : "SECURITY",
                        "DENIED", "Unauthorized / security violation: " + ex.getMessage(), meta);
            } catch (Exception ignored) {}
        }
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
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
        return new ResponseEntity<>(ApiResponse.error("Access denied"), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(new ApiResponse<>(false, "Validation failed", errors), HttpStatus.BAD_REQUEST);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        log.error("Unhandled server error in product-service: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error("An unexpected error occurred. Please try again later."), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
