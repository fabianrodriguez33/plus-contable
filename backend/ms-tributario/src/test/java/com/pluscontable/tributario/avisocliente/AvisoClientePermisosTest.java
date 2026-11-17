package com.pluscontable.tributario.avisocliente;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

/** HU-33: el cliente lee los avisos de su empresa y de ninguna otra; el estudio lee los de cualquiera. */
@WebMvcTest(AvisoClienteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AvisoClientePermisosTest {
    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean AvisoClienteService service;

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) token.claim("clienteId", clienteId);
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void elClienteLeeLosAvisosDeSuEmpresa() throws Exception {
        mvc.perform(get("/api/tributario/clientes/7/avisos-pago").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(status().isOk());
        verify(service).listar(7L);
    }

    @Test
    void elClienteNoLeeLosAvisosDeOtraEmpresaNiSinClienteIdEnElToken() throws Exception {
        mvc.perform(get("/api/tributario/clientes/8/avisos-pago").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tributario/clientes/8/avisos-pago").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", null)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void elEstudioLeeLosAvisosDeCualquierCliente() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get("/api/tributario/clientes/8/avisos-pago").header(HttpHeaders.AUTHORIZATION, bearer(rol, null)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/tributario/clientes/8/avisos-pago")).andExpect(status().isUnauthorized());
    }
}
