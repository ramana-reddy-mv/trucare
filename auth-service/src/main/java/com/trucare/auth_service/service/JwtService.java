package com.trucare.auth_service.service;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final String issuer;

    public JwtService(@Value("${security.jwt.secret-base64}") String secret,
                      @Value("${security.jwt.issuer:trucare-auth-service}") String issuer) {
        this.secret = secret;
        this.issuer = issuer;
        getSignKey();
    }

    public String createToken(String username, List<String> roles, long expirationSeconds) {
        Instant issuedAt = Instant.now();
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("JWT roles must not be empty");
        }
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(issuer)
                .claim("roles", roles)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(getSignKey(), SignatureAlgorithm.HS256).compact();
    }

    private Key getSignKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("security.jwt.secret-base64 must be valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT signing secret must decode to at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public void validateToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(getSignKey()).requireIssuer(issuer)
                .setAllowedClockSkewSeconds(30).build().parseClaimsJws(token).getBody();
        Object roles = claims.get("roles");
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || !(roles instanceof Collection<?> values)
                || values.isEmpty()
                || values.stream().anyMatch(role -> !(role instanceof String))) {
            throw new JwtException("JWT subject or role claims are missing");
        }
    }
}
