package com.pluscontable.tributario.recordatoriopago;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

/** HU-15 · T41: solo CONTADOR y ADMIN programan, consultan y cancelan; el cliente MYPE recibe 403 y sin sesión 401. */
@WebMvcTest(RecordatorioPagoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RecordatorioPagoPermisosTest {
    private static final String RUTA = "/api/tributario/obligaciones/7/recordatorios-pago";
    private static final String CUERPO = "{\"fechaRecordatorio\":\"2026-11-20\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean RecordatorioPagoService service;

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return "Bearer " + b.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void contadorYAdminProgramanConsultanYCancelan() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null)).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isCreated());
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null))).andExpect(status().isOk());
            mvc.perform(patch(RUTA + "/3/cancelacion").header(HttpHeaders.AUTHORIZATION, bearer(rol, null))).andExpect(status().isOk());
        }
        verify(service, org.mockito.Mockito.times(2)).programar(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 11, 20)), org.mockito.ArgumentMatchers.eq("usuario-prueba"));
        verify(service, org.mockito.Mockito.times(2)).cancelar(7L, 3L);
    }

    @Test
    void clienteMypeNoProgramaNiConsultaNiCancela_aunqueSeaLaObligacionDeSuEmpresa() throws Exception {
        String cliente = bearer("CLIENTE_MYPE", 7L);
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, cliente).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());
        mvc.perform(patch(RUTA + "/3/cancelacion").header(HttpHeaders.AUTHORIZATION, cliente)).andExpect(status().isForbidden());
        verify(service, never()).programar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any());
        verify(service, never()).cancelar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinSesionRecibe401() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(patch(RUTA + "/3/cancelacion")).andExpect(status().isUnauthorized());
    }
}
