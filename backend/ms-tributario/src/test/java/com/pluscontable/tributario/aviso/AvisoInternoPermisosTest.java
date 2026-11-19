package com.pluscontable.tributario.aviso;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@WebMvcTest(AvisoInternoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AvisoInternoPermisosTest {
    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean AvisoInternoService service;

    private String bearer(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void contadorYAdminPuedenConsultarYMarcarLeido() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get("/api/tributario/avisos").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk());
            mvc.perform(patch("/api/tributario/avisos/1/lectura").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk());
        }
        verify(service, org.mockito.Mockito.times(2)).bandeja(null);
        verify(service, org.mockito.Mockito.times(2)).marcarLeido(1L);
    }

    @Test
    void clienteMypeNoAccedeALaBandejaNiALaLectura() throws Exception {
        mvc.perform(get("/api/tributario/avisos").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/tributario/avisos/1/lectura").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        verify(service, never()).bandeja(null);
        verify(service, never()).marcarLeido(1L);
    }

    @Test
    void anonimoNoAccedeALaBandejaNiALaLectura() throws Exception {
        mvc.perform(get("/api/tributario/avisos")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/tributario/avisos/1/lectura")).andExpect(status().isUnauthorized());
        verify(service, never()).bandeja(null);
        verify(service, never()).marcarLeido(1L);
    }
}
