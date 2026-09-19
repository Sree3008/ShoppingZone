package com.eshoppingzone.order.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.eshoppingzone.order.audit.service.AuditLogService auditLogService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(cto -> {})
                        .frameOptions(fo -> fo.deny())
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (auditLogService != null) {
                                try {
                                    java.util.Map<String, Object> meta = new java.util.HashMap<>();
                                    if (request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                                    if (request.getMethod() != null) meta.put("method", request.getMethod());
                                    auditLogService.log("UNAUTHORIZED_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                                            "Unauthorized access: " + authException.getMessage(), meta);
                                } catch (Exception ignored) {}
                            }
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (auditLogService != null) {
                                try {
                                    java.util.Map<String, Object> meta = new java.util.HashMap<>();
                                    if (request.getRequestURI() != null) meta.put("endpoint", request.getRequestURI());
                                    if (request.getMethod() != null) meta.put("method", request.getMethod());
                                    auditLogService.log("FORBIDDEN_ACCESS", "SECURITY", request.getRequestURI(), "DENIED",
                                            "Access denied: " + accessDeniedException.getMessage(), meta);
                                } catch (Exception ignored) {}
                            }
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"message\":\"Access denied\",\"data\":null}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(
                                "/api/v1/orders/*/internal",
                                "/api/v1/orders/*/status/internal"
                        ).hasAnyRole("INTERNAL", "ADMIN")
                        .requestMatchers("/api/v1/audit-logs/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
