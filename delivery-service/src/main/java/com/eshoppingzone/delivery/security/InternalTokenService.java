package com.eshoppingzone.delivery.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class InternalTokenService {

    @Value("${jwt.secret:${JWT_SECRET}}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateInternalToken() {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 3600000); // 1 hour validity

        return Jwts.builder()
                .subject("internal-delivery-service")
                .claim("role", "INTERNAL")
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
}
