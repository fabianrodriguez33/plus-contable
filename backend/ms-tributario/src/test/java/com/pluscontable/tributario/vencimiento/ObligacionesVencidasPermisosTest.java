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

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return "Bearer " + b.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static ObligacionesVencidasService.Resultado conUnaDeCadaLista() {
        var o = new ObligacionVencidaDto(1L, 1L, "20100070970", "Acme", null, java.time.LocalDate.of(2026, 10, 1),
                java.time.LocalDate.of(2026, 11, 1), 3, com.pluscontable.tributario.model.Obligacion.Estado.DECLARADO, null, null, null);
        return new ObligacionesVencidasService.Resultado(List.of(o), List.of(o));
    }

    @Test
    void contadorYAdminConsultanLaCarteraConLasDosListas() throws Exception {
        org.mockito.Mockito.when(service.listar(null)).thenReturn(conUnaDeCadaLista());
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.vencidas.length()").value(1))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sinDeclarar.length()").value(1));
        }
        verify(service, org.mockito.Mockito.times(2)).listar(null);
    }

    @Test
    void elClienteVeSusVencidasPeroNoLaListaDeSinDeclarar() throws Exception {
        org.mockito.Mockito.when(service.listar(7L)).thenReturn(conUnaDeCadaLista());
        mvc.perform(get(RUTA + "?clienteId=7").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.vencidas.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sinDeclarar.length()").value(0));
    }

    @Test
    void elClienteNuncaVeLaCarteraNiLaDeOtraEmpresaNiSinIndicarLaSuya() throws Exception {
        String suyo = bearer("CLIENTE_MYPE", 7L);
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, suyo)).andExpect(status().isForbidden());                 // sin clienteId: sería la cartera
        mvc.perform(get(RUTA + "?clienteId=8").header(HttpHeaders.AUTHORIZATION, suyo)).andExpect(status().isForbidden());
        mvc.perform(get(RUTA + "?clienteId=7").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", null))).andExpect(status().isForbidden());
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA + "?clienteId=7")).andExpect(status().isUnauthorized());
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any());
    }
}
