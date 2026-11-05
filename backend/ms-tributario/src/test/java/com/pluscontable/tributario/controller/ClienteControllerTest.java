package com.pluscontable.tributario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.service.ClienteService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
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

/** HU-01: solo el estudio (contador o administrador) autenticado registra y consulta clientes. */
@WebMvcTest(controllers = ClienteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ClienteControllerTest {

    private static final String CUERPO = """
            {"ruc":"20100070970","razonSocial":"Empresa SAC","regimenTributario":"MYPE","condicionHabido":"HABIDO"}""";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean ClienteService clienteService;

    private String bearer(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private void registrar(String authorization, int estadoEsperado) throws Exception {
        var r = post("/api/tributario/clientes").contentType(MediaType.APPLICATION_JSON).content(CUERPO);
        if (authorization != null) r.header(HttpHeaders.AUTHORIZATION, authorization);
        mvc.perform(r).andExpect(status().is(estadoEsperado));
    }

    @Test
    void elContadorAutenticadoRegistraClientes() throws Exception {
        when(clienteService.crear(any())).thenReturn(new ClienteDto(1L, "20100070970", "Empresa SAC",
                Cliente.Regimen.MYPE, Cliente.CondicionHabido.HABIDO, null, null, null, null, null, false));

        registrar(bearer("CONTADOR"), 201);
        verify(clienteService).crear(any());
    }

    @Test
    void elAdministradorDelEstudioTambienPuede() throws Exception {
        when(clienteService.crear(any())).thenReturn(new ClienteDto(1L, "20100070970", "Empresa SAC",
                Cliente.Regimen.MYPE, Cliente.CondicionHabido.HABIDO, null, null, null, null, null, false));

        registrar(bearer("ADMIN"), 201);
    }

    @Test
    void elClienteMypeNoRegistraNiConsultaClientes() throws Exception {
        registrar(bearer("CLIENTE_MYPE"), 403);
        mvc.perform(get("/api/tributario/clientes").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        verify(clienteService, never()).crear(any());
    }

    @Test
    void unTokenSinRolNoRegistraClientes() throws Exception {
        registrar(bearer(), 403);
        verify(clienteService, never()).crear(any());
    }

    @Test
    void sinSesionNoSeRegistraNada() throws Exception {
        registrar(null, 401);
        mvc.perform(get("/api/tributario/clientes")).andExpect(status().isUnauthorized());
        verify(clienteService, never()).crear(any());
    }

    /** Envía un cuerpo y devuelve el mensaje de la respuesta 400 (el servicio nunca debe llamarse). */
    private String rechazado(String cuerpo) throws Exception {
        String respuesta = mvc.perform(post("/api/tributario/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        verify(clienteService, never()).crear(any());
        return respuesta;
    }

    @Test
    void sinRazonSocialSeRechazaIndicandoElCampo() throws Exception {
        assertThat(rechazado("""
                {"ruc":"20100070970","regimenTributario":"MYPE"}""")).contains("La razón social es obligatoria");
    }

    @Test
    void razonSocialVaciaOSoloEspaciosSeRechaza() throws Exception {
        assertThat(rechazado("""
                {"ruc":"20100070970","razonSocial":"","regimenTributario":"MYPE"}""")).contains("La razón social es obligatoria");
        assertThat(rechazado("""
                {"ruc":"20100070970","razonSocial":"   ","regimenTributario":"MYPE"}""")).contains("La razón social es obligatoria");
    }

    @Test
    void sinRegimenSeRechazaIndicandoElCampo() throws Exception {
        assertThat(rechazado("""
                {"ruc":"20100070970","razonSocial":"Empresa SAC"}""")).contains("El régimen tributario es obligatorio");
    }

    @Test
    void regimenNuloExplicitoSeRechaza() throws Exception {
        assertThat(rechazado("""
                {"ruc":"20100070970","razonSocial":"Empresa SAC","regimenTributario":null}""")).contains("El régimen tributario es obligatorio");
    }

    @Test
    void sinRucSeRechaza() throws Exception {
        assertThat(rechazado("""
                {"razonSocial":"Empresa SAC","regimenTributario":"MYPE"}""")).contains("El RUC es obligatorio");
    }

    @Test
    void variosCamposFaltantesSeInformanJuntos() throws Exception {
        String r = rechazado("{}");
        assertThat(r).contains("La razón social es obligatoria").contains("El régimen tributario es obligatorio")
                .contains("El RUC es obligatorio");
    }
}
