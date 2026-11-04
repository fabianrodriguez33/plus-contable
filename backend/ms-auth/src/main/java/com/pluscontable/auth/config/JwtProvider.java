package com.pluscontable.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration expiration;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public Duration getExpiration() {
        return expiration;
    }

    public String generate(String username, List<String> roles) {
        return generate(username, roles, null);
    }

    /** HU-38: el claim clienteId solo va en el token de una cuenta de cliente; el del estudio no lo lleva. */
    public String generate(String username, List<String> roles, Long clienteId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles);
        if (clienteId != null) builder.claim("clienteId", clienteId);
        return builder
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(key)
                .compact();
    }

    /** @return claims si el token es valido; null en caso contrario */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
