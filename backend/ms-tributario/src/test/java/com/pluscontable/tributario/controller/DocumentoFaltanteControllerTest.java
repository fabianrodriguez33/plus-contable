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
import com.pluscontable.tributario.dto.DocumentoFaltanteDto;
import com.pluscontable.tributario.model.EstadoDocumentoFaltante;
import com.pluscontable.tributario.service.DocumentoFaltanteService;
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

/** HU-24 · Tarea 173 (parte HTTP): contrato de los documentos faltantes y sus permisos; solo el estudio. */
@WebMvcTest(controllers = DocumentoFaltanteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DocumentoFaltanteControllerTest {

    private static final String RUTA = "/api/tributario/clientes/7/documentos-faltantes";
    private static final String CUERPO = "{\"documento\":\"Libro de compras\",\"fechaLimite\":\"2026-11-20\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean DocumentoFaltanteService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject(usuario).claim("roles", List.of(roles));
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static DocumentoFaltanteDto dto(EstadoDocumentoFaltante estado, Long incidencia) {
        return new DocumentoFaltanteDto(3L, 7L, "Libro de compras", LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 20), estado,
                estado == EstadoDocumentoFaltante.ENTREGADO ? LocalDate.of(2026, 11, 15) : null, incidencia);
    }

    @Test
    void elContadorRegistraConSuUsuarioYVeLaListaConElEstado() throws Exception {
        when(service.registrar(eq(7L), any(), eq("contador1"))).thenReturn(dto(EstadoDocumentoFaltante.PENDIENTE, null));
        when(service.listar(7L)).thenReturn(List.of(dto(EstadoDocumentoFaltante.SIN_RESPUESTA, null)));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO).header(HttpHeaders.AUTHORIZATION, bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fechaSolicitud").value("2026-11-11"));
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("contador1", "CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("SIN_RESPUESTA"));
    }

    @Test
    void elAdministradorRegistraLaEntregaYLaIncidenciaConSuUsuario() throws Exception {
        when(service.registrarEntrega(7L, 3L)).thenReturn(dto(EstadoDocumentoFaltante.ENTREGADO, null));
        when(service.registrarComoIncidencia(7L, 3L, "admin")).thenReturn(dto(EstadoDocumentoFaltante.SIN_RESPUESTA, 40L));

        mvc.perform(post(RUTA + "/3/entrega").header(HttpHeaders.AUTHORIZATION, bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.fechaEntrega").value("2026-11-15"));
        mvc.perform(post(RUTA + "/3/incidencia").header(HttpHeaders.AUTHORIZATION, bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidenciaId").value(40));
    }

    @Test
    void unRechazoDelServicioDa400ConSuMensaje() throws Exception {
        when(service.registrarComoIncidencia(eq(7L), eq(3L), any()))
                .thenThrow(new ValidationException("Solo se registra como incidencia un documento sin respuesta"));

        mvc.perform(post(RUTA + "/3/incidencia").header(HttpHeaders.AUTHORIZATION, bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Solo se registra como incidencia un documento sin respuesta"));
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403EnLasCuatroRutasSinLlamarAlServicio() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        String cliente = bearer("cliente1", "CLIENTE_MYPE");
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO).header(HttpHeaders.AUTHORIZATION, cliente))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());
        mvc.perform(post(RUTA + "/3/entrega").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());
        mvc.perform(post(RUTA + "/3/incidencia").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());

        verify(service, never()).registrar(any(), any(), any());
        verify(service, never()).listar(any());
        verify(service, never()).registrarEntrega(any(), any());
        verify(service, never()).registrarComoIncidencia(any(), any(), any());
    }
}
