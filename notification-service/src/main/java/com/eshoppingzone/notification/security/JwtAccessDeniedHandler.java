package com.eshoppingzone.notification.security;

import com.eshoppingzone.notification.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.notification.audit.service.AuditLogService auditLogService;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (auditLogService != null) {
            try {
                java.util.Map<String, Object> meta = new java.util.HashMap<>();
                if (request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                if (request.getMethod() != null) meta.put("method", request.getMethod());
                auditLogService.log("FORBIDDEN_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                        "Access denied: " + accessDeniedException.getMessage(), meta);
            } catch (Exception ignored) {}
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Void> errorResponse = ApiResponse.error("Access denied");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
