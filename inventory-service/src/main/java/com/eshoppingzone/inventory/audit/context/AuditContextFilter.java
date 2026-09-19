package com.eshoppingzone.inventory.audit.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.trim();
        }

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put("correlationId", correlationId);

        AuditContext.AuditContextData context = AuditContext.get();
        context.setCorrelationId(correlationId);
        context.setHttpMethod(request.getMethod());
        context.setEndpoint(request.getRequestURI());

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddr();
        } else {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        context.setIpAddress(ipAddress);
        context.setUserAgent(request.getHeader("User-Agent"));

        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                context.setActorUserId(Long.parseLong(headerUserId.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        String headerUsername = request.getHeader("X-User-Name");
        if (headerUsername != null && !headerUsername.isBlank()) {
            context.setActorUsername(headerUsername.trim());
        }
        String headerRole = request.getHeader("X-User-Role");
        if (headerRole != null && !headerRole.isBlank()) {
            context.setActorRole(headerRole.trim());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
            MDC.remove("correlationId");
        }
    }
}
