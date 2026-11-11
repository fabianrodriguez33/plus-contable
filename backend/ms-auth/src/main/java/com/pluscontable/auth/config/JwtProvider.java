package com.pluscontable.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    public static final String CLAIM_CAMBIAR_PASSWORD = "cambiarPassword";

    private final SecretKey key;
    private final Duration expiration;
    private final Duration cambioPasswordExpiration;

    /** Vigencia del token restringido del primer ingreso si no se configura: 15 minutos bastan para cambiar la contraseña. */
    static final long CAMBIO_PASSWORD_MINUTOS_POR_DEFECTO = 15;

    public JwtProvider(String secret, long expirationHours) {
        this(secret, expirationHours, CAMBIO_PASSWORD_MINUTOS_POR_DEFECTO);
    }

    @Autowired
    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-hours}") long expirationHours,
                       @Value("${app.jwt.cambio-password-minutes:15}") long cambioPasswordMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
        this.cambioPasswordExpiration = Duration.ofMinutes(cambioPasswordMinutes);
    }

    public Duration getExpiration() {
        return expiration;
    }

    /** Vigencia del token restringido del primer ingreso (HU-37). */
    public Duration getCambioPasswordExpiration() {
        return cambioPasswordExpiration;
    }

    public String generate(String username, List<String> roles) {
        return generate(username, roles, null);
    }

    /**
     * HU-38: el claim clienteId solo va en el token de una cuenta de cliente; el del estudio no lo lleva.
     * HU-36 (tarea 2): todo token lleva un jti unico para poder revocarlo al cerrar sesion.
     */
    public String generate(String username, List<String> roles, Long clienteId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("roles", roles);
        if (clienteId != null) builder.claim("clienteId", clienteId);
        return builder
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(key)
                .compact();
    }

    /**
     * HU-37: token del primer ingreso con la contraseña temporal. No lleva roles (ms-tributario le responde 403 en toda ruta) y
     * ms-auth solo lo deja usar para cambiar la contraseña y cerrar sesión; se revoca al cambiarla.
     */
    public String generateCambioPassword(String username) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("roles", List.of())
                .claim(CLAIM_CAMBIAR_PASSWORD, true)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + cambioPasswordExpiration.toMillis()))
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
