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
import com.pluscontable.tributario.service.PagoHonorarioService;
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

/** HU-28 y HU-29: contrato HTTP de la programación de cobros y de sus pagos; solo el estudio programa, consulta y registra pagos. */
@WebMvcTest(controllers = CobroHonorarioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CobroHonorarioControllerTest {

    private static final String PROGRAMAR = "/api/tributario/clientes/7/cobros/programacion";
    private static final String PENDIENTES = "/api/tributario/clientes/7/cobros";
    private static final String CUERPO = "{\"periodicidad\":\"MENSUAL\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean CobroHonorarioService service;
    @MockBean PagoHonorarioService pagos;

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
        return new CobroHonorarioDto(1L, LocalDate.of(2026, 11, 30), new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"), EstadoCobro.PENDIENTE);
    }

    // ---- HU-29 · Tarea 143 (contrato HTTP y permisos del pago) ----

    private static final String PAGAR = "/api/tributario/clientes/7/cobros/1/pagos";
    private static final String PAGO = "{\"fechaPago\":\"2026-11-10\",\"monto\":200.00,\"medioPago\":\"TRANSFERENCIA\"}";

    @Test
    void elContadorRegistraUnPagoParcialYRecibe201ConElSaldoYQuienLoRegistro() throws Exception {
        when(pagos.registrar(eq(7L), eq(1L), any(), eq("contador1"))).thenReturn(new CobroHonorarioDto(1L, LocalDate.of(2026, 11, 30),
                new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("300.00"), EstadoCobro.PAGO_PARCIAL));

        mvc.perform(con(post(PAGAR).contentType(MediaType.APPLICATION_JSON).content(PAGO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGO_PARCIAL"))
                .andExpect(jsonPath("$.pagado").value(200.00))
                .andExpect(jsonPath("$.saldo").value(300.00));
    }

    @Test
    void pagarUnCobroYaPagadoDa400YUnCobroAjenoDa404() throws Exception {
        when(pagos.registrar(eq(7L), eq(1L), any(), any()))
                .thenThrow(new ValidationException("Solo se registran pagos sobre un cobro pendiente: este cobro ya está pagado"));
        when(pagos.registrar(eq(7L), eq(2L), any(), any())).thenThrow(new EntityNotFoundException("Cobro no encontrado"));

        mvc.perform(con(post(PAGAR).contentType(MediaType.APPLICATION_JSON).content(PAGO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se registran pagos sobre un cobro pendiente: este cobro ya está pagado"));
        mvc.perform(con(post("/api/tributario/clientes/7/cobros/2/pagos").contentType(MediaType.APPLICATION_JSON).content(PAGO),
                        bearer("admin", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unMedioDePagoInventadoDa400SinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(PAGAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaPago\":\"2026-11-10\",\"monto\":200,\"medioPago\":\"CHEQUE_VOLADOR\"}"), bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest());
        verify(pagos, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403AlPagarSinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(PAGAR).contentType(MediaType.APPLICATION_JSON).content(PAGO), null)).andExpect(status().isUnauthorized());
        mvc.perform(con(post(PAGAR).contentType(MediaType.APPLICATION_JSON).content(PAGO), bearer("cliente1", "CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        verify(pagos, never()).registrar(any(), any(), any(), any());
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
