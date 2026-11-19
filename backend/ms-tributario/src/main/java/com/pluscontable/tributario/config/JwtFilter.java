package com.pluscontable.tributario.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Valida el JWT emitido por ms-auth (mismo secreto HMAC). */
@Component
public class JwtFilter extends OncePerRequestFilter {

    /** Mismo claim que pone ms-auth en el token del primer ingreso. */
    static final String CLAIM_CAMBIAR_PASSWORD = "cambiarPassword";

    private final SecretKey key;

    public JwtFilter(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                // HU-37: el token restringido del primer ingreso (contraseña temporal) solo sirve en ms-auth para cambiarla.
                // Aquí se rechaza por su claim, sin depender de que no tenga roles: una ruta futura con authenticated() no lo deja pasar
                if (Boolean.TRUE.equals(claims.get(CLAIM_CAMBIAR_PASSWORD, Boolean.class))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"mensaje\":\"Debe cambiar la contraseña temporal antes de usar el sistema\"}");
                    return;
                }
                List<?> roles = claims.get("roles", List.class);
                var authorities = (roles == null ? List.<SimpleGrantedAuthority>of()
                        : roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
                var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                // HU-38: empresa de la cuenta de cliente; el token del estudio no lo trae
                Number clienteId = claims.get("clienteId", Number.class);
                auth.setDetails(new DatosToken(clienteId == null ? null : clienteId.longValue()));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ignored) {
                // token invalido/expirado: el request queda sin autenticar y recibe 401
            }
        }
        chain.doFilter(request, response);
    }
}
