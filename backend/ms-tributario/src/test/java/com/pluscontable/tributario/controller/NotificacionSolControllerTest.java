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

    // ---- tarea 67: GET /clientes/{id}/notificaciones

    private MockHttpServletRequestBuilder listar(String parametros, String autorizacion) {
        var r = get(RUTA + parametros);
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static NotificacionSolDto de(long id, String plazo, EstadoNotificacion estado) {
        return new NotificacionSolDto(id, 7L, TipoNotificacion.CARTA_INDUCTIVA, LocalDate.of(2026, 11, 1),
                LocalDate.parse(plazo), estado, estado == EstadoNotificacion.ATENDIDA ? LocalDate.of(2026, 11, 9) : null);
    }

    @Test
    void elContadorListaLosPendientesYElOrdenDelServicioSeConserva() throws Exception {
        when(service.pendientes(7L)).thenReturn(List.of(de(2, "2026-11-12", EstadoNotificacion.PENDIENTE),
                de(1, "2026-11-30", EstadoNotificacion.PENDIENTE)));

        mvc.perform(listar("?estado=PENDIENTE", bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].plazoRespuesta").value("2026-11-12"))
                .andExpect(jsonPath("$[0].tipo").value("CARTA_INDUCTIVA"))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].estado").value("PENDIENTE"));
        verify(service, never()).listar(any(), any());
    }

    @Test
    void conEstadoAtendidaYSinEstadoSePidenAlServicioConEseFiltro() throws Exception {
        when(service.listar(7L, EstadoNotificacion.ATENDIDA)).thenReturn(List.of(de(3, "2026-11-05", EstadoNotificacion.ATENDIDA)));
        when(service.listar(7L, null)).thenReturn(List.of(de(3, "2026-11-05", EstadoNotificacion.ATENDIDA),
                de(2, "2026-11-12", EstadoNotificacion.PENDIENTE)));

        mvc.perform(listar("?estado=ATENDIDA", bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fechaAtencion").value("2026-11-09"));
        mvc.perform(listar("", bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        verify(service, never()).pendientes(any());
    }

    @Test
    void elAdministradorDelEstudioTambienLista() throws Exception {
        when(service.pendientes(7L)).thenReturn(List.of());

        mvc.perform(listar("?estado=PENDIENTE", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listarSinSesionDa401YConUnClienteMypeDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(listar("?estado=PENDIENTE", null)).andExpect(status().isUnauthorized());
        mvc.perform(listar("?estado=PENDIENTE", bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(listar("", bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).pendientes(any());
        verify(service, never()).listar(any(), any());
    }

    @Test
    void listarDeUnClienteInexistenteResponde404ConElMensaje() throws Exception {
        when(service.pendientes(7L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 7"));

        mvc.perform(listar("?estado=PENDIENTE", bearer("CONTADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Cliente no encontrado: 7"));
    }

    @Test
    void unEstadoQueNoEsPendienteNiAtendidaDa400SinLlamarAlServicio() throws Exception {
        mvc.perform(listar("?estado=ANULADA", bearer("CONTADOR"))).andExpect(status().isBadRequest());

        verify(service, never()).pendientes(any());
        verify(service, never()).listar(any(), any());
    }

    // ---- tarea 68: PATCH /notificaciones/{id}/atencion

    private static final String RUTA_ATENCION = "/api/tributario/notificaciones/55/atencion";

    private MockHttpServletRequestBuilder atender(String autorizacion) {
        var r = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(RUTA_ATENCION);
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    @Test
    void elContadorMarcaAtendidaYRecibe200ConLaFechaDeAtencion() throws Exception {
        when(service.marcarAtendida(55L)).thenReturn(new NotificacionSolDto(55L, 7L, TipoNotificacion.ESQUELA,
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20), EstadoNotificacion.ATENDIDA, LocalDate.of(2026, 11, 9)));

        mvc.perform(atender(bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.fechaAtencion").value("2026-11-09"));
    }

    @Test
    void atenderSinSesionDa401YConUnClienteMypeDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(atender(null)).andExpect(status().isUnauthorized());
        mvc.perform(atender(bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).marcarAtendida(any());
    }

    @Test
    void siNoExisteResponde404YSiYaEstabaAtendidaResponde400ConSuMensaje() throws Exception {
        when(service.marcarAtendida(55L))
                .thenThrow(new EntityNotFoundException("Notificación no encontrada"))
                .thenThrow(new ValidationException("La notificación ya fue atendida"));

        mvc.perform(atender(bearer("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Notificación no encontrada"));
        mvc.perform(atender(bearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La notificación ya fue atendida"));
    }
}
