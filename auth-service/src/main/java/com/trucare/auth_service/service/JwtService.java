package com.trucare.auth_service.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.private-key-base64}") String encodedPrivateKey,
            @Value("${security.jwt.public-key-base64:${TRUCARE_JWT_PUBLIC_KEY_BASE64}}") String encodedPublicKey,
            @Value("${security.jwt.issuer:trucare-auth-service}") String issuer) {
        this.privateKey = readPrivateKey(encodedPrivateKey);
        this.publicKey = readPublicKey(encodedPublicKey);
        this.issuer = issuer;
    }

    public String createToken(String username, List<String> roles, long expirationSeconds) {
        Instant issuedAt = Instant.now();
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("JWT roles must not be empty");
        }
        return Jwts.builder()
                .setSubject(username)
                .setIssuer(issuer)
                .setAudience("trucare-api")
                .claim("roles", roles)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public void validateToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .requireIssuer(issuer)
                .requireAudience("trucare-api")
                .setAllowedClockSkewSeconds(30)
                .build()
                .parseClaimsJws(token)
                .getBody();
        Object roles = claims.get("roles");
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || !(roles instanceof Collection<?> values)
                || values.isEmpty()
                || values.stream().anyMatch(role -> !(role instanceof String))) {
            throw new JwtException("JWT subject or role claims are missing");
        }
    }

    private RSAPrivateKey readPrivateKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA private key must be at least 2048 bits");
            }
            return key;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("security.jwt.private-key-base64 must contain a valid RSA private key",
                    exception);
        }
    }

    private RSAPublicKey readPublicKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA public key must be at least 2048 bits");
            }
            return key;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("security.jwt.public-key-base64 must contain a valid RSA public key",
                    exception);
        }
    }
}
