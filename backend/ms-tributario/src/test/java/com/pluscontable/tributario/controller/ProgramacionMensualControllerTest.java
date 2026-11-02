package com.pluscontable.tributario.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.ProgramacionMesDto;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.service.ProgramacionMensualService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
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

/** HU-07: contrato HTTP de POST /api/tributario/obligaciones/programacion (con JWT firmados como ms-auth). */
@WebMvcTest(controllers = ProgramacionMensualController.class)
@Import(SecurityConfig.class)
class ProgramacionMensualControllerTest {
    private static final String RUTA = "/api/tributario/obligaciones/programacion";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean ProgramacionMensualService servicio;

    private MockHttpServletRequestBuilder peticion(String cuerpo) {
        String token = Jwts.builder().subject("contador").claim("roles", List.of("CONTADOR"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8))).compact();
        return post(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void devuelveLoCreadoLoQueYaExistiaYLoQueNoSeGenera() throws Exception {
        when(servicio.programar(7L, YearMonth.of(2026, 10))).thenReturn(new ProgramacionMesDto(
                YearMonth.of(2026, 10), LocalDate.of(2026, 11, 20), List.of(),
                List.of(TipoObligacion.IGV), List.of(TipoObligacion.AFP)));

        mvc.perform(peticion("{\"clienteId\":7,\"periodo\":\"2026-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("2026-10"))
                .andExpect(jsonPath("$.fechaVencimiento").value("2026-11-20"))
                .andExpect(jsonPath("$.creadas", hasSize(0)))
                .andExpect(jsonPath("$.yaExistian[0]").value("IGV"))
                .andExpect(jsonPath("$.noGeneradas[0]").value("AFP"));
        verify(servicio).programar(7L, YearMonth.of(2026, 10));
    }

    @Test
    void sinCronogramaResponde400ConElMensaje() throws Exception {
        when(servicio.programar(7L, YearMonth.of(2027, 1)))
                .thenThrow(new ValidationException("Falta el cronograma de 2027-01"));

        mvc.perform(peticion("{\"clienteId\":7,\"periodo\":\"2027-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el cronograma de 2027-01"));
    }

    @Test
    void clienteInexistenteResponde404() throws Exception {
        when(servicio.programar(99L, YearMonth.of(2026, 10)))
                .thenThrow(new EntityNotFoundException("Cliente no encontrado"));

        mvc.perform(peticion("{\"clienteId\":99,\"periodo\":\"2026-10\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Cliente no encontrado"));
    }

    @Test
    void unPeriodoMalEscritoResponde400SinLlamarAlServicio() throws Exception {
        mvc.perform(peticion("{\"clienteId\":7,\"periodo\":\"octubre\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(servicio, never()).programar(any(), any());
    }
}
