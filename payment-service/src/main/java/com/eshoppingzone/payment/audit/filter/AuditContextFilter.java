package com.eshoppingzone.payment.audit.filter;

import com.eshoppingzone.payment.audit.context.AuditContext;
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
        try {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            AuditContext.setCorrelationId(correlationId);
            MDC.put("correlationId", correlationId);

            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            } else if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            AuditContext.setClientIp(ip);

            String userAgent = request.getHeader("User-Agent");
            AuditContext.setUserAgent(userAgent);
            AuditContext.setHttpMethod(request.getMethod());
            AuditContext.setRequestUri(request.getRequestURI());

            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
            MDC.remove("correlationId");
        }
    }
}
