package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.ProgramacionCobroDto;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.Periodicidad;
import com.pluscontable.tributario.service.CobroHonorarioService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HU-28: contrato HTTP de la programación de cobros; solo el estudio programa y consulta. */
@WebMvcTest(controllers = CobroHonorarioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CobroHonorarioControllerTest {

    private static final String PROGRAMAR = "/api/tributario/clientes/7/cobros/programacion";
    private static final String PENDIENTES = "/api/tributario/clientes/7/cobros";
    private static final String CUERPO = "{\"periodicidad\":\"MENSUAL\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean CobroHonorarioService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject(usuario).claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder r, String autorizacion) {
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static CobroHonorarioDto cobro() {
        return new CobroHonorarioDto(1L, LocalDate.of(2026, 11, 30), new BigDecimal("500.00"), EstadoCobro.PENDIENTE);
    }

    @Test
    void elContadorProgramaYRecibe201ConLosCobros() throws Exception {
        when(service.programar(eq(7L), any())).thenReturn(new ProgramacionCobroDto(Periodicidad.MENSUAL, 12, 0, List.of(cobro())));

        mvc.perform(con(post(PROGRAMAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodicidad").value("MENSUAL"))
                .andExpect(jsonPath("$.cobrosGenerados").value(12))
                .andExpect(jsonPath("$.cobros[0].fechaCobro").value("2026-11-30"))
                .andExpect(jsonPath("$.cobros[0].estado").value("PENDIENTE"));
    }

    @Test
    void sinAcuerdoDa400ConElMensajeParaRegistrarloPrimero() throws Exception {
        when(service.programar(eq(7L), any())).thenThrow(new ValidationException("Registre primero el acuerdo de honorarios del cliente"));

        mvc.perform(con(post(PROGRAMAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Registre primero el acuerdo de honorarios del cliente"));
    }

    @Test
    void unaPeriodicidadInventadaDa400SinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(PROGRAMAR).contentType(MediaType.APPLICATION_JSON).content("{\"periodicidad\":\"SEMANAL\"}"),
                        bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest());
        verify(service, never()).programar(any(), any());
    }

    @Test
    void elAdministradorVeLosPendientesYElClienteInexistenteDa404() throws Exception {
        when(service.pendientes(7L)).thenReturn(List.of(cobro()));
        when(service.pendientes(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado"));

        mvc.perform(con(get(PENDIENTES), bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].monto").value(500.00));
        mvc.perform(con(get("/api/tributario/clientes/99/cobros"), bearer("admin", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403EnLasDosRutasSinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(PROGRAMAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), null)).andExpect(status().isUnauthorized());
        mvc.perform(con(get(PENDIENTES), null)).andExpect(status().isUnauthorized());
        mvc.perform(con(post(PROGRAMAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("cliente1", "CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        mvc.perform(con(get(PENDIENTES), bearer("cliente1", "CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).programar(any(), any());
        verify(service, never()).pendientes(any());
    }
}
