package com.eshoppingzone.notification.security;

import com.eshoppingzone.notification.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.notification.audit.service.AuditLogService auditLogService;

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        if (auditLogService != null) {
            try {
                java.util.Map<String, Object> meta = new java.util.HashMap<>();
                if (request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                if (request.getMethod() != null) meta.put("method", request.getMethod());
                auditLogService.log("UNAUTHORIZED_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                        "Unauthorized access: " + authException.getMessage(), meta);
            } catch (Exception ignored) {}
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> errorResponse = ApiResponse.error("Unauthorized");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
