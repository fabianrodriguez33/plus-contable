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
import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.service.IncidenciaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** HU-23: contrato HTTP de las incidencias; solo el estudio registra y el usuario sale del token. */
@WebMvcTest(controllers = IncidenciaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class IncidenciaControllerTest {

    private static final String REGISTRAR = "/api/tributario/incidencias";
    private static final String HISTORIAL = "/api/tributario/clientes/7/incidencias";
    private static final String CUERPO = """
            {"tipo":"CLIENTE_NO_ENVIA_DOCUMENTOS","clienteId":7,"fecha":"2026-11-09","descripcion":"No envió las facturas"}""";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean IncidenciaService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject(usuario).claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder r, String autorizacion) {
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static IncidenciaDto incidencia() {
        return new IncidenciaDto(50L, TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS, LocalDate.of(2026, 11, 9),
                "No envió las facturas", "contador1", LocalDateTime.of(2026, 11, 9, 10, 0), 1);
    }

    @Test
    void elContadorRegistraYRecibe201ConElUsuarioDelToken() throws Exception {
        when(service.registrar(any(), eq("contador1"))).thenReturn(incidencia());

        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.tipo").value("CLIENTE_NO_ENVIA_DOCUMENTOS"))
                .andExpect(jsonPath("$.registradaPor").value("contador1"))
                .andExpect(jsonPath("$.clientesAsociados").value(1));
    }

    @Test
    void elUsuarioDelCuerpoNoSeUsa() throws Exception {
        when(service.registrar(any(), eq("contador1"))).thenReturn(incidencia());

        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"OTRO\",\"clienteId\":7,\"fecha\":\"2026-11-09\",\"descripcion\":\"x\",\"registradaPor\":\"otro\"}"),
                        bearer("contador1", "CONTADOR")))
                .andExpect(status().isCreated());

        verify(service).registrar(any(), eq("contador1"));
    }

    @Test
    void elAdministradorTambienRegistra() throws Exception {
        when(service.registrar(any(), eq("admin"))).thenReturn(incidencia());

        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("admin", "ADMIN")))
                .andExpect(status().isCreated());
    }

    @Test
    void sinSesionDa401YUnClienteMypeDa403EnLasDosRutasSinLlamarAlServicio() throws Exception {
        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), null)).andExpect(status().isUnauthorized());
        mvc.perform(con(get(HISTORIAL), null)).andExpect(status().isUnauthorized());
        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("cliente1", "CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        mvc.perform(con(get(HISTORIAL), bearer("cliente1", "CLIENTE_MYPE"))).andExpect(status().isForbidden());

        verify(service, never()).registrar(any(), any());
        verify(service, never()).delCliente(any());
    }

    @Test
    void losDatosFaltantesDan400ConSuMensaje() throws Exception {
        when(service.registrar(any(), any())).thenThrow(new ValidationException("Faltan o son inválidos: descripcion"));

        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"OTRO\",\"clienteId\":7}"),
                        bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: descripcion"));
    }

    @Test
    void unTipoDesconocidoOUnJsonMalFormadoDan400() throws Exception {
        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INVENTADO\",\"clienteId\":7,\"fecha\":\"2026-11-09\",\"descripcion\":\"x\"}"),
                        bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content("{no es json"), bearer("contador1", "CONTADOR")))
                .andExpect(status().isBadRequest());

        verify(service, never()).registrar(any(), any());
    }

    @Test
    void unClienteInexistenteDa404() throws Exception {
        when(service.registrar(any(), any())).thenThrow(new EntityNotFoundException("Cliente no encontrado: 7"));

        mvc.perform(con(post(REGISTRAR).contentType(MediaType.APPLICATION_JSON).content(CUERPO), bearer("contador1", "CONTADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Cliente no encontrado: 7"));
    }

    @Test
    void elHistorialDelClienteDevuelveSusIncidencias() throws Exception {
        when(service.delCliente(7L)).thenReturn(List.of(incidencia()));

        mvc.perform(con(get(HISTORIAL), bearer("contador1", "CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descripcion").value("No envió las facturas"));
    }

    @Test
    void elHistorialDeUnClienteInexistenteDa404() throws Exception {
        when(service.delCliente(7L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 7"));

        mvc.perform(con(get(HISTORIAL), bearer("contador1", "CONTADOR"))).andExpect(status().isNotFound());
    }
}
