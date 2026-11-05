package com.pluscontable.tributario.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.pluscontable.tributario.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica la política existente sin alterar SecurityConfig ni la matriz HU-38. */
@WebMvcTest(controllers = CalendarioController.class)
@Import(SecurityConfig.class)
class CalendarioPermisosTest {
    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean CalendarioService calendarioService;

    private String token(String rol) {
        return Jwts.builder().subject("usuario").claim("roles", List.of(rol))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test void contadorPuedeConsultar() throws Exception {
        when(calendarioService.listar(1L, null, null, null, null, null)).thenReturn(List.of());
        assertThat(mvc.perform(get("/api/tributario/calendario?clienteId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CONTADOR")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test void adminPuedeConsultar() throws Exception {
        when(calendarioService.listar(1L, null, null, null, null, null)).thenReturn(List.of());
        assertThat(mvc.perform(get("/api/tributario/calendario?clienteId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test void clienteMypeNoPuedeConsultar() throws Exception {
        assertThat(mvc.perform(get("/api/tributario/calendario?clienteId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CLIENTE_MYPE")))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test void sinAutenticacionNoPuedeConsultar() throws Exception {
        assertThat(mvc.perform(get("/api/tributario/calendario?clienteId=1"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
