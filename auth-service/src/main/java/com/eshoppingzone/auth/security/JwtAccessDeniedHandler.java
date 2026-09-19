package com.eshoppingzone.auth.security;

import com.eshoppingzone.auth.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.eshoppingzone.auth.audit.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (auditLogService != null) {
            try {
                auditLogService.log("FORBIDDEN_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                        "Access denied to resource: " + request.getRequestURI(),
                        Map.of("endpoint", request.getRequestURI(), "method", request.getMethod()));
            } catch (Exception ignored) {
            }
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Object> errorResponse = ApiResponse.error("Access denied");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
