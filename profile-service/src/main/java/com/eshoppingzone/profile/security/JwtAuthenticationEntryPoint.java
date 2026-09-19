package com.eshoppingzone.profile.security;

import com.eshoppingzone.profile.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.eshoppingzone.profile.audit.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (auditLogService != null) {
            try {
                auditLogService.log("UNAUTHORIZED_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                        "Unauthorized request to resource: " + request.getRequestURI(),
                        Map.of("endpoint", request.getRequestURI(), "method", request.getMethod()));
            } catch (Exception ignored) {
            }
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Object> errorResponse = ApiResponse.error("Unauthorized");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
