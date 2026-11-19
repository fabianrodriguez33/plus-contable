package com.pluscontable.tributario.vencimiento;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

/** HU-16 · permisos: solo CONTADOR y ADMIN; el cliente MYPE recibe 403 y sin sesión 401. */
@WebMvcTest(ObligacionesVencidasController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ObligacionesVencidasPermisosTest {
    private static final String RUTA = "/api/tributario/obligaciones-vencidas";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean ObligacionesVencidasService service;

    private String bearer(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void contadorYAdminConsultan() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol))).andExpect(status().isOk());
        }
        verify(service, org.mockito.Mockito.times(2)).listar(null);
    }

    @Test
    void clienteMypeRecibe403YAnonimo401() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(get(RUTA + "?clienteId=1").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any());
    }
}
