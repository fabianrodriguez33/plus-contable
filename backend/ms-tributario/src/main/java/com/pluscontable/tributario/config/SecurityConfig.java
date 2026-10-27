package com.pluscontable.tributario.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ADMIN existe en ms-auth (administrador del estudio) y tiene los mismos permisos que CONTADOR
    static final String[] ESTUDIO = {"CONTADOR", "ADMIN"};
    static final String[] ESTUDIO_Y_CLIENTE = {"CONTADOR", "ADMIN", "CLIENTE_MYPE"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // HU-38: matriz de permisos (docs/sprint1-jeanpierre/HU-38.md). Lo no listado es solo del estudio.
                .authorizeHttpRequests(a -> a
                        // El contenedor reenvia los errores a /error SIN volver a ejecutar JwtFilter: sin esta regla
                        // todo error (404, 409, 500...) se evaluaba como anonimo y salia como 401.
                        // Solo abre el reenvio interno de errores; GET /error directo sigue exigiendo token.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tributario/health").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/tributario/clientes/*/trabajadores",
                                "/api/tributario/clientes/*/documentos",
                                "/api/tributario/clientes/*/documentos/*/archivo",
                                "/api/tributario/clientes/*/acuerdo",
                                "/api/tributario/obligaciones",
                                "/api/tributario/obligaciones/*")
                        .hasAnyRole(ESTUDIO_Y_CLIENTE)
                        .anyRequest().hasAnyRole(ESTUDIO))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
