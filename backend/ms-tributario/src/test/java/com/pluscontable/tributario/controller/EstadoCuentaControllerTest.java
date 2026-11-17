package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.EstadoCuentaDto;
import com.pluscontable.tributario.service.EstadoCuentaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

/** HU-30 · Tarea 149 (parte HTTP): contrato del estado de cuenta y sus permisos; solo el estudio lo emite. */
@WebMvcTest(controllers = EstadoCuentaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EstadoCuentaControllerTest {

    private static final String RUTA = "/api/tributario/clientes/7/estado-cuenta?desde=2026-11-01&hasta=2026-11-30";
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 30);

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean EstadoCuentaService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject(usuario).claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static EstadoCuentaDto vacio() {
        return new EstadoCuentaDto(7L, "Acme SAC", DESDE, HASTA, new BigDecimal("300.00"), List.of(), List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"), true);
    }

    @Test
    void elContadorYElAdministradorRecibenElEstadoDeCuentaDelPeriodo() throws Exception {
        when(service.emitir(7L, DESDE, HASTA)).thenReturn(vacio());

        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("u", rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.razonSocial").value("Acme SAC"))
                    .andExpect(jsonPath("$.desde").value("2026-11-01"))
                    .andExpect(jsonPath("$.saldo").value(300.00))
                    .andExpect(jsonPath("$.sinMovimientos").value(true));
        }
    }

    @Test
    void sinFechasLasPideElServicioYUnaFechaMalEscritaDa400() throws Exception {
        when(service.emitir(eq(7L), eq(null), eq(null))).thenThrow(new ValidationException("Faltan o son inválidos: desde, hasta"));

        mvc.perform(get("/api/tributario/clientes/7/estado-cuenta").header(HttpHeaders.AUTHORIZATION, bearer("u", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: desde, hasta"));
        mvc.perform(get("/api/tributario/clientes/7/estado-cuenta?desde=11/2026&hasta=2026-11-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer("u", "CONTADOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unClienteInexistenteDa404() throws Exception {
        when(service.emitir(eq(99L), any(), any())).thenThrow(new EntityNotFoundException("Cliente no encontrado"));

        mvc.perform(get("/api/tributario/clientes/99/estado-cuenta?desde=2026-11-01&hasta=2026-11-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer("u", "CONTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("cliente1", "CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).emitir(any(), any(), any());
    }
}
