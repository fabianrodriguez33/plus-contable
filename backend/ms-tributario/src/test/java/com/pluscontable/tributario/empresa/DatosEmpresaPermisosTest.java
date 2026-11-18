package com.pluscontable.tributario.empresa;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HU-35, criterios 2 y 4: el cliente solo lee y solo los datos de su empresa. */
@WebMvcTest(DatosEmpresaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DatosEmpresaPermisosTest {
    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean DatosEmpresaService service;

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) token.claim("clienteId", clienteId);
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void elClienteLeeLosDatosDeSuEmpresa() throws Exception {
        mvc.perform(get("/api/tributario/clientes/7/datos-empresa").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(status().isOk());
        verify(service).obtener(7L);
    }

    @Test
    void criterio4_elClienteNoVeLosDatosDeOtraEmpresaNiSinClienteIdEnElToken() throws Exception {
        mvc.perform(get("/api/tributario/clientes/8/datos-empresa").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tributario/clientes/8/datos-empresa").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", null)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void elEstudioYSinTokenSeComportanComoEnElRestoDeLaMatriz() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get("/api/tributario/clientes/8/datos-empresa").header(HttpHeaders.AUTHORIZATION, bearer(rol, null)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/tributario/clientes/8/datos-empresa")).andExpect(status().isUnauthorized());
    }

    private void prohibido(MockHttpServletRequestBuilder peticion) throws Exception {
        mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void criterio2_elClienteNoPuedeModificarSusDatosSociosNiDocumentos() throws Exception {
        prohibido(put("/api/tributario/clientes/7"));
        prohibido(put("/api/tributario/clientes/7/socios"));
        prohibido(post("/api/tributario/clientes/7/socios"));
        prohibido(patch("/api/tributario/clientes/7/socios/1/aporte"));
        prohibido(post("/api/tributario/clientes/7/documentos"));
        prohibido(put("/api/tributario/clientes/7/sol"));
        prohibido(post("/api/tributario/clientes/7/datos-empresa"));
        prohibido(put("/api/tributario/clientes/7/datos-empresa"));
        prohibido(patch("/api/tributario/clientes/7/datos-empresa"));
        prohibido(delete("/api/tributario/clientes/7/datos-empresa"));
    }
}
