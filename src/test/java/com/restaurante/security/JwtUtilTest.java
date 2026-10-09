package com.restaurante.security;

import com.restaurante.model.domain.enums.RolUsuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String TEST_SECRET = java.util.ResourceBundle.getBundle("application").getString("jwt.secret");
    private final JwtUtil jwt = new JwtUtil(TEST_SECRET, 3600000);

    @Test
    void configuracionInseguraFallaSinRevelarSecret() {
        for (String secret : new String[]{"no-base64!", "YQ==", ""}) {
            var exception = assertThrows(IllegalArgumentException.class, () -> new JwtUtil(secret, 1000));
            assertEquals("JWT_SECRET debe ser Base64 de al menos 32 bytes", exception.getMessage());
            assertNull(exception.getCause());
        }
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(TEST_SECRET, 0));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(TEST_SECRET, -1));
    }

    @Test
    void tokensNulosVaciosMalformadosNoSonValidos() {
        assertFalse(jwt.isValid(null));
        assertFalse(jwt.isValid(""));
        assertFalse(jwt.isValid("a.b.c"));
    }

    @Test
    void rolesSeConservanSinDuplicarPrefijo() {
        for (RolUsuario rol : RolUsuario.values()) {
            String token = jwt.generateToken("usuario@example.test", rol);
            assertTrue(jwt.isValid(token));
            assertEquals(rol.name(), jwt.extractClaims(token).get("rol"));
        }
    }

    @Test
    void claimsConRolDesconocidoSubjectVacioOIssuedAtAusenteSonInvalidos() {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        Instant now = Instant.now();
        String unknown = Jwts.builder().subject("a@example.test").claim("rol", "ROLE_ROLE_GERENTE")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();
        String missingSubject = Jwts.builder().claim("rol", "ROLE_GERENTE")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();
        String missingIat = Jwts.builder().subject("a@example.test").claim("rol", "ROLE_GERENTE")
                .expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();
        assertFalse(jwt.isValid(unknown));
        assertFalse(jwt.isValid(missingSubject));
        assertFalse(jwt.isValid(missingIat));
    }
}
