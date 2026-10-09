package com.restaurante.security;

import com.restaurante.model.domain.enums.RolUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        try {
            key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT_SECRET debe ser Base64 de al menos 32 bytes");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS debe ser positivo");
        }
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, RolUsuario rol) {
        Instant now = Instant.now();
        return Jwts.builder().subject(email).claim("rol", rol.name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key).compact();
    }

    public Claims extractClaims(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getExpiration() == null || claims.getIssuedAt() == null) {
            throw new IllegalArgumentException("Claims JWT incompletos");
        }
        String rol = claims.get("rol", String.class);
        if (rol == null) {
            throw new IllegalArgumentException("Rol JWT ausente");
        }
        RolUsuario.valueOf(rol);
        return claims;
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
