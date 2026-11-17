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
import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.TipoSolicitud;
import com.pluscontable.tributario.service.SolicitudEspecialService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

/** HU-26 · Tarea 188 (parte HTTP): contrato de las solicitudes especiales y sus permisos; solo el estudio. */
@WebMvcTest(controllers = SolicitudEspecialController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SolicitudEspecialControllerTest {

    private static final String RUTA = "/api/tributario/clientes/7/solicitudes";
    private static final String CUERPO = "{\"tipo\":\"CONSTANCIA\",\"descripcion\":\"No adeudo\",\"fechaEntrega\":\"2026-11-20\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean SolicitudEspecialService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject(usuario).claim("roles", List.of(roles));
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static SolicitudEspecialDto dto(EstadoSolicitud estado) {
        return new SolicitudEspecialDto(5L, 7L, TipoSolicitud.CONSTANCIA, "No adeudo", LocalDate.of(2026, 11, 20), estado,
                estado == EstadoSolicitud.ATENDIDA ? LocalDate.of(2026, 11, 12) : null);
    }

    @Test
    void elContadorRegistraConSuUsuarioYRecibe201ComoPendiente() throws Exception {
        when(service.registrar(eq(7L), any(), eq("contador1"))).thenReturn(dto(EstadoSolicitud.PENDIENTE));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO).header(HttpHeaders.AUTHORIZATION, bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONSTANCIA"))
                .andExpect(jsonPath("$.fechaEntrega").value("2026-11-20"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void sinFechaDeEntregaDa400YUnTipoInventadoTambienSinLlamarAlServicio() throws Exception {
        when(service.registrar(eq(7L), any(), any())).thenThrow(new ValidationException("Faltan o son inválidos: fechaEntrega"));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"TRAMITE\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: fechaEntrega"));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CARTA_NOTARIAL\",\"fechaEntrega\":\"2026-11-20\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAdministradorVeLosPendientesYMarcaUnoAtendido() throws Exception {
        when(service.pendientes(7L)).thenReturn(List.of(dto(EstadoSolicitud.PENDIENTE)));
        when(service.marcarAtendida(7L, 5L)).thenReturn(dto(EstadoSolicitud.ATENDIDA));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("admin", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(5));
        mvc.perform(post(RUTA + "/5/atender").header(HttpHeaders.AUTHORIZATION, bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.fechaAtencion").value("2026-11-12"));
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403EnLasTresRutasSinLlamarAlServicio() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        String cliente = bearer("cliente1", "CLIENTE_MYPE");
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO).header(HttpHeaders.AUTHORIZATION, cliente))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());
        mvc.perform(post(RUTA + "/5/atender").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());

        verify(service, never()).registrar(any(), any(), any());
        verify(service, never()).pendientes(any());
        verify(service, never()).marcarAtendida(any(), any());
    }
}
