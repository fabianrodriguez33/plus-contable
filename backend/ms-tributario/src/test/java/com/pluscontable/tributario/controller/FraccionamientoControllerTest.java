package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.CalendarioCuotaDto;
import com.pluscontable.tributario.dto.CuotaDto;
import com.pluscontable.tributario.dto.FraccionamientoDto;
import com.pluscontable.tributario.model.EstadoCuota;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.service.FraccionamientoService;
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

/** HU-17: contrato HTTP del fraccionamiento, el calendario de cuotas y el pago de una cuota; solo el estudio. */
@WebMvcTest(controllers = FraccionamientoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FraccionamientoControllerTest {

    private static final String BASE = "/api/tributario/clientes/7/fraccionamientos";
    private static final String CUERPO = """
            {"tributo":"IGV","montoTotal":1200.00,"numeroCuotas":4,"fechaPrimeraCuota":"2026-12-15"}""";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean FraccionamientoService service;

    private String bearer(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder r, String autorizacion) {
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static CuotaDto cuota(boolean enRiesgo) {
        return new CuotaDto(100L, 1, LocalDate.of(2026, 12, 15), new BigDecimal("300.00"), EstadoCuota.PENDIENTE, null, enRiesgo);
    }

    private static FraccionamientoDto fraccionamiento() {
        return new FraccionamientoDto(21L, 7L, TipoObligacion.IGV, new BigDecimal("1200.00"), 4, LocalDate.of(2026, 12, 15), List.of(cuota(false)));
    }

    @Test
    void elContadorRegistraYRecibe201ConLasCuotas() throws Exception {
        when(service.registrar(eq(7L), any())).thenReturn(fraccionamiento());

        mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.tributo").value("IGV"))
                .andExpect(jsonPath("$.cuotas[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.cuotas[0].enRiesgo").value(false));
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403EnTodasLasRutasSinLlamarAlServicio() throws Exception {
        for (String mype : new String[]{null, bearer("CLIENTE_MYPE")}) {
            int esperado = mype == null ? 401 : 403;
            mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CUERPO), mype)).andExpect(status().is(esperado));
            mvc.perform(con(get(BASE), mype)).andExpect(status().is(esperado));
            mvc.perform(con(get(BASE + "/calendario"), mype)).andExpect(status().is(esperado));
            mvc.perform(con(patch("/api/tributario/cuotas/100/pago"), mype)).andExpect(status().is(esperado));
        }

        verify(service, never()).registrar(any(), any());
        verify(service, never()).listar(any());
        verify(service, never()).calendario(any(), any(), any());
        verify(service, never()).pagarCuota(any());
    }

    @Test
    void faltandoDatosElServicioRechazaYLaRespuestaEsUn400ConElMensaje() throws Exception {
        when(service.registrar(eq(7L), any())).thenThrow(new ValidationException("Faltan o son inválidos: numeroCuotas"));

        mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tributo":"IGV","montoTotal":1200.00,"fechaPrimeraCuota":"2026-12-15"}"""), bearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: numeroCuotas"));
    }

    @Test
    void unTributoInvalidoOUnaFechaMalFormadaDan400SinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tributo":"OTRO","montoTotal":1200.00,"numeroCuotas":4,"fechaPrimeraCuota":"2026-12-15"}"""), bearer("CONTADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tributo":"IGV","montoTotal":1200.00,"numeroCuotas":4,"fechaPrimeraCuota":"mañana"}"""), bearer("CONTADOR")))
                .andExpect(status().isBadRequest());

        verify(service, never()).registrar(any(), any());
    }

    @Test
    void siElClienteNoExisteResponde404ConElMensaje() throws Exception {
        when(service.registrar(eq(7L), any())).thenThrow(new EntityNotFoundException("Cliente no encontrado: 7"));

        mvc.perform(con(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("CONTADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Cliente no encontrado: 7"));
    }

    @Test
    void listarDevuelveLosFraccionamientosDelCliente() throws Exception {
        when(service.listar(7L)).thenReturn(List.of(fraccionamiento()));

        mvc.perform(con(get(BASE), bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroCuotas").value(4))
                .andExpect(jsonPath("$[0].cuotas.length()").value(1));
    }

    @Test
    void elCalendarioRecibeLasFechasYDevuelveLasCuotasConSuRiesgo() throws Exception {
        when(service.calendario(7L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(new CalendarioCuotaDto(cuota(true), 21L, -4, true)));

        mvc.perform(con(get(BASE + "/calendario?desde=2026-11-01&hasta=2026-12-31"), bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cuota.enRiesgo").value(true))
                .andExpect(jsonPath("$[0].diasRestantes").value(-4))
                .andExpect(jsonPath("$[0].vencida").value(true));
    }

    @Test
    void unaFechaDelCalendarioMalFormadaDa400() throws Exception {
        mvc.perform(con(get(BASE + "/calendario?desde=ayer"), bearer("CONTADOR"))).andExpect(status().isBadRequest());

        verify(service, never()).calendario(any(), any(), any());
    }

    @Test
    void pagarUnaCuotaDevuelveLaCuotaPagada() throws Exception {
        when(service.pagarCuota(100L)).thenReturn(new CuotaDto(100L, 1, LocalDate.of(2026, 12, 15), new BigDecimal("300.00"),
                EstadoCuota.PAGADA, LocalDate.of(2026, 11, 9), false));

        mvc.perform(con(patch("/api/tributario/cuotas/100/pago"), bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.fechaPago").value("2026-11-09"));
    }

    @Test
    void pagarUnaCuotaInexistenteDa404YUnaYaPagadaDa400ConSuMensaje() throws Exception {
        when(service.pagarCuota(100L))
                .thenThrow(new EntityNotFoundException("Cuota no encontrada"))
                .thenThrow(new ValidationException("La cuota ya fue pagada"));

        mvc.perform(con(patch("/api/tributario/cuotas/100/pago"), bearer("CONTADOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Cuota no encontrada"));
        mvc.perform(con(patch("/api/tributario/cuotas/100/pago"), bearer("CONTADOR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("La cuota ya fue pagada"));
    }
}
