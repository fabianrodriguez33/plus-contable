package com.pluscontable.tributario.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.MorosoDto;
import com.pluscontable.tributario.service.MorosoService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;

/** HU-31 · Tarea 155 (parte HTTP): contrato de la lista de morosos y criterio 3, solo el estudio la ve. */
@WebMvcTest(controllers = MorosoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MorosoControllerTest {

    private static final String RUTA = "/api/tributario/morosos";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean MorosoService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject(usuario).claim("roles", List.of(roles));
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void criterio3_elContadorYElAdministradorVenLaListaConMontoYDiasDeAtraso() throws Exception {
        when(service.listar()).thenReturn(List.of(
                new MorosoDto(4L, "20123456789", "Acme SAC", new BigDecimal("750.00"), 2, LocalDate.of(2026, 10, 15), 27)));

        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("u", rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].razonSocial").value("Acme SAC"))
                    .andExpect(jsonPath("$[0].montoVencido").value(750.00))
                    .andExpect(jsonPath("$[0].diasAtraso").value(27))
                    .andExpect(jsonPath("$[0].vencidoDesde").value("2026-10-15"));
        }
    }

    @Test
    void criterio3_sinSesionDa401YUnClienteMypeOUnTokenSinRolDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("cliente1", "CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("nadie"))).andExpect(status().isForbidden());

        verify(service, never()).listar();
    }
}
