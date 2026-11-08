package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.NotificacionSolDto;
import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.service.NotificacionSolService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
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

/** HU-18 · Tarea 64: contrato HTTP de registrar una notificación; solo el estudio la registra. */
@WebMvcTest(controllers = NotificacionSolController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificacionSolControllerTest {

    private static final String RUTA = "/api/tributario/clientes/7/notificaciones";
    private static final String CUERPO = """
            {"tipo":"ESQUELA","fechaRecepcion":"2026-11-05","plazoRespuesta":"2026-11-20"}""";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean NotificacionSolService service;

    private String bearer(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private MockHttpServletRequestBuilder registrar(String cuerpo, String autorizacion) {
        var r = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static NotificacionSolDto dto() {
        return new NotificacionSolDto(55L, 7L, TipoNotificacion.ESQUELA, LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 20), EstadoNotificacion.PENDIENTE, null);
    }

    @Test
    void elContadorRegistraYRecibe201ConLaNotificacionPendiente() throws Exception {
        when(service.registrar(eq(7L), any())).thenReturn(dto());

        mvc.perform(registrar(CUERPO, bearer("CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.tipo").value("ESQUELA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.plazoRespuesta").value("2026-11-20"));
    }

    @Test
    void elAdministradorDelEstudioTambienRegistra() throws Exception {
        when(service.registrar(eq(7L), any())).thenReturn(dto());

        mvc.perform(registrar(CUERPO, bearer("ADMIN"))).andExpect(status().isCreated());
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(registrar(CUERPO, null)).andExpect(status().isUnauthorized());
        mvc.perform(registrar(CUERPO, bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).registrar(any(), any());
    }

    @Test
    void sinPlazoElServicioRechazaYLaRespuestaEsUn400ConElMensaje() throws Exception {
        when(service.registrar(eq(7L), any()))
                .thenThrow(new ValidationException("Faltan o son inválidos: plazoRespuesta"));

        mvc.perform(registrar("""
                        {"tipo":"ESQUELA","fechaRecepcion":"2026-11-05"}""", bearer("CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: plazoRespuesta"));
    }

    @Test
    void siElClienteNoExisteResponde404ConElMensaje() throws Exception {
        when(service.registrar(eq(7L), any())).thenThrow(new EntityNotFoundException("Cliente no encontrado: 7"));

        mvc.perform(registrar(CUERPO, bearer("CONTADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Cliente no encontrado: 7"));
    }

    @Test
    void unTipoQueNoEsEsquelaCartaInductivaNiComunicadoDa400SinLlamarAlServicio() throws Exception {
        mvc.perform(registrar("""
                        {"tipo":"OTRO","fechaRecepcion":"2026-11-05","plazoRespuesta":"2026-11-20"}""", bearer("CONTADOR")))
                .andExpect(status().isBadRequest());

        verify(service, never()).registrar(any(), any());
    }
}
