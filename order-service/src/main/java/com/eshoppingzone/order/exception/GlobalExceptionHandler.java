package com.eshoppingzone.order.exception;

import com.eshoppingzone.order.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidOrderState(InvalidOrderStateException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.eshoppingzone.order.returns.exception.InvalidReturnStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidReturnState(com.eshoppingzone.order.returns.exception.InvalidReturnStateException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(com.eshoppingzone.order.returns.exception.DuplicateReturnException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateReturn(com.eshoppingzone.order.returns.exception.DuplicateReturnException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientStock(InsufficientStockException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Object>> handlePaymentException(PaymentException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.PAYMENT_REQUIRED);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.order.audit.service.AuditLogService auditLogService;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        log.error("Unhandled server error: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error("An unexpected error occurred. Please try again later."), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
