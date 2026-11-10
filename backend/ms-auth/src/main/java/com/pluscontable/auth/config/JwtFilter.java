package com.pluscontable.auth.config;

import com.pluscontable.auth.repository.TokenRevocadoRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

    /** HU-37: lo único que permite el token restringido del primer ingreso. */
    static final Set<String> RUTAS_DEL_PRIMER_INGRESO = Set.of("/api/auth/cambiar-password", "/api/auth/logout");

    private final JwtProvider jwtProvider;
    private final TokenRevocadoRepository revocados;

    public JwtFilter(JwtProvider jwtProvider, TokenRevocadoRepository revocados) {
        this.jwtProvider = jwtProvider;
        this.revocados = revocados;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtProvider.parse(header.substring(7));
            // HU-36 (tarea 2): un token cuya sesion se cerro (jti revocado) queda sin autenticar y recibe 401
            String jti = claims == null ? null : claims.getId();
            if (claims != null && (jti == null || !revocados.existsById(jti))) {
                List<?> roles = claims.get("roles", List.class);
                var authorities = (roles == null ? List.<SimpleGrantedAuthority>of()
                        : roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
                var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                Date exp = claims.getExpiration();
                boolean cambiarPassword = Boolean.TRUE.equals(claims.get(JwtProvider.CLAIM_CAMBIAR_PASSWORD, Boolean.class));
                auth.setDetails(new SesionToken(jti, exp == null ? null : exp.toInstant(), cambiarPassword));
                // HU-37: con la contraseña temporal solo se puede cambiarla (o cerrar sesión): lo demás se niega
                if (cambiarPassword && !RUTAS_DEL_PRIMER_INGRESO.contains(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"mensaje\":\"Debe cambiar la contraseña temporal antes de usar el sistema\"}");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
