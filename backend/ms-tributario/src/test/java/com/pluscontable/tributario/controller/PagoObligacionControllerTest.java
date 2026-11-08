package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.PagoEntradaDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.service.PagoObligacionService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** HU-13: contrato HTTP de registrar un pago (multipart); solo el estudio lo registra. */
@WebMvcTest(controllers = PagoObligacionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PagoObligacionControllerTest {

    private static final String RUTA = "/api/tributario/obligaciones/3/pagos";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean PagoObligacionService service;

    private String bearer(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "constancia.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
    }

    private MockMultipartHttpServletRequestBuilder pago(String autorizacion, boolean conArchivo) {
        MockMultipartHttpServletRequestBuilder r = multipart(RUTA);
        if (conArchivo) r.file(pdf());
        r.param("fechaPago", "2026-11-08").param("montoPagado", "400.00").param("numeroConstancia", "C-001");
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static ObligacionDto respuesta() {
        return new ObligacionDto(3L, 9L, TipoObligacion.IGV, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 20),
                Obligacion.Estado.PAGO_PARCIAL, LocalDate.of(2026, 11, 5), "F-1", new BigDecimal("1000.00"),
                new BigDecimal("400.00"), new BigDecimal("600.00"), 1);
    }

    @Test
    void elContadorRegistraYRecibe201ConElEstadoElAcumuladoYElSaldo() throws Exception {
        when(service.registrar(eq(3L), any(), any())).thenReturn(respuesta());

        mvc.perform(pago(bearer("CONTADOR"), true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGO_PARCIAL"))
                .andExpect(jsonPath("$.montoAcumuladoPagado").value(400.00))
                .andExpect(jsonPath("$.saldo").value(600.00))
                .andExpect(jsonPath("$.numeroPagos").value(1));
    }

    @Test
    void alServicioLeLlegaLaFechaElMontoLaConstanciaYElArchivo() throws Exception {
        when(service.registrar(eq(3L), any(), any())).thenReturn(respuesta());

        mvc.perform(pago(bearer("ADMIN"), true)).andExpect(status().isCreated());

        ArgumentCaptor<PagoEntradaDto> dto = ArgumentCaptor.forClass(PagoEntradaDto.class);
        verify(service).registrar(eq(3L), dto.capture(), any());
        org.assertj.core.api.Assertions.assertThat(dto.getValue().fechaPago()).isEqualTo(LocalDate.of(2026, 11, 8));
        org.assertj.core.api.Assertions.assertThat(dto.getValue().montoPagado()).isEqualByComparingTo("400.00");
        org.assertj.core.api.Assertions.assertThat(dto.getValue().numeroConstancia()).isEqualTo("C-001");
    }

    @Test
    void sinArchivoElServicioRechazaConUn400DeTodosLosErrores() throws Exception {
        when(service.registrar(eq(3L), any(), eq(null)))
                .thenThrow(new ValidationException("Faltan o son inválidos: archivo (el comprobante es obligatorio)"));

        mvc.perform(pago(bearer("CONTADOR"), false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: archivo (el comprobante es obligatorio)"));
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(pago(null, true)).andExpect(status().isUnauthorized());
        mvc.perform(pago(bearer("CLIENTE_MYPE"), true)).andExpect(status().isForbidden());

        verify(service, never()).registrar(any(), any(), any());
    }

    @Test
    void siLaObligacionNoExisteResponde404YSiNoEstaDeclaradaResponde400ConSuMensaje() throws Exception {
        when(service.registrar(eq(3L), any(), any()))
                .thenThrow(new EntityNotFoundException("Obligación no encontrada"))
                .thenThrow(new ValidationException("Solo se registra pago sobre una obligación ya declarada"));

        mvc.perform(pago(bearer("CONTADOR"), true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Obligación no encontrada"));
        mvc.perform(pago(bearer("CONTADOR"), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se registra pago sobre una obligación ya declarada"));
    }

    @Test
    void unaFechaOUnMontoMalFormadosDan400SinLlamarAlServicio() throws Exception {
        mvc.perform(multipart(RUTA).file(pdf()).param("fechaPago", "ayer").param("montoPagado", "400").param("numeroConstancia", "C-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(RUTA).file(pdf()).param("fechaPago", "2026-11-08").param("montoPagado", "mucho").param("numeroConstancia", "C-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isBadRequest());

        verify(service, never()).registrar(any(), any(), any());
    }

    @Test
    void unCuerpoQueNoEsMultipartDa415() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(RUTA)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isUnsupportedMediaType());
    }
}
