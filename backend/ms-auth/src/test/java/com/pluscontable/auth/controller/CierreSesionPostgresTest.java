package com.pluscontable.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluscontable.auth.config.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-36 (tareas 2 y 3): cierre de sesion de punta a punta con el servicio completo y una base PostgreSQL real
 * (desechable). Login, uso del token, logout, rechazo del token revocado y limpieza de vencidos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CierreSesionPostgresTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @Value("${app.seed.admin-username}") String admin;
    @Value("${app.seed.admin-password}") String clave;

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auth_schema.tokens_revocados");
        jdbc.update("DELETE FROM auth_schema.bitacora_seguridad WHERE evento = 'LOGOUT'");
    }

    private String entrar() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", admin, "password", clave))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private int estadoMe(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private int estadoLogout(String token) throws Exception {
        return mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void existeLaTablaDeTokensRevocados() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'auth_schema' AND table_name = 'tokens_revocados'
                """, String.class);

        assertThat(columnas).containsExactlyInAnyOrder("jti", "username", "expira_en", "revocado_en");
    }

    @Test
    void despuesDeCerrarSesionElTokenYaNoSirve() throws Exception {
        String token = entrar();
        assertThat(estadoMe(token)).isEqualTo(200);

        assertThat(estadoLogout(token)).isEqualTo(204);

        assertThat(estadoMe(token)).isEqualTo(401);
        assertThat(estadoLogout(token)).isEqualTo(401);
    }

    @Test
    void elLogoutGuardaElJtiConSuVencimientoYAnotaElEventoEnLaBitacora() throws Exception {
        String token = entrar();
        var claims = jwt.parse(token);

        estadoLogout(token);

        var fila = jdbc.queryForMap("SELECT jti, username, expira_en FROM auth_schema.tokens_revocados");
        assertThat(fila.get("jti")).isEqualTo(claims.getId());
        assertThat(fila.get("username")).isEqualTo(admin);
        assertThat(((java.sql.Timestamp) fila.get("expira_en")).toLocalDateTime())
                .isAfter(java.time.LocalDateTime.now().plusHours(7));
        Integer eventos = jdbc.queryForObject(
                "SELECT count(*) FROM auth_schema.bitacora_seguridad WHERE evento = 'LOGOUT' AND username = ?",
                Integer.class, admin);
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    void cerrarUnaSesionNoAfectaOtraSesionDelMismoUsuario() throws Exception {
        String uno = entrar();
        Thread.sleep(1100); // otro iat/jti
        String dos = entrar();

        estadoLogout(uno);

        assertThat(estadoMe(uno)).isEqualTo(401);
        assertThat(estadoMe(dos)).isEqualTo(200);
    }

    @Test
    void sinSesionNoSeAccedeNiSeCierraSesion() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        assertThat(estadoLogout("token-que-no-es-un-jwt")).isEqualTo(401);
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM auth_schema.tokens_revocados", Integer.class);
        assertThat(filas).isZero();
    }

    @Test
    void alCerrarSesionSeLimpianLosRevocadosQueYaVencieron() throws Exception {
        jdbc.update("INSERT INTO auth_schema.tokens_revocados (jti, username, expira_en) VALUES (?, ?, now() - interval '1 hour')",
                "jti-viejo", admin);
        jdbc.update("INSERT INTO auth_schema.tokens_revocados (jti, username, expira_en) VALUES (?, ?, now() + interval '1 hour')",
                "jti-vigente", admin);

        estadoLogout(entrar());

        List<String> jtis = jdbc.queryForList("SELECT jti FROM auth_schema.tokens_revocados", String.class);
        assertThat(jtis).contains("jti-vigente").doesNotContain("jti-viejo").hasSize(2);
    }
}
