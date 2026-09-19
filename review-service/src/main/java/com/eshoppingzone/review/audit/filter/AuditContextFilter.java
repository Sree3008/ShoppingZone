package com.eshoppingzone.review.audit.filter;

import com.eshoppingzone.review.audit.context.AuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = request.getHeader("X-Request-ID");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            AuditContext.setCorrelationId(correlationId);
            response.setHeader("X-Correlation-ID", correlationId);

            String clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isBlank()) {
                clientIp = request.getRemoteAddr();
            } else if (clientIp.contains(",")) {
                clientIp = clientIp.split(",")[0].trim();
            }
            AuditContext.setClientIp(clientIp);

            AuditContext.setUserAgent(request.getHeader("User-Agent"));
            AuditContext.setHttpMethod(request.getMethod());
            AuditContext.setRequestUri(request.getRequestURI());

            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
        }
    }
}
