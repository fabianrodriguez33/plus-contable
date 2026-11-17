package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.dto.HistorialItemDto.Origen;
import com.pluscontable.tributario.service.HistorialService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.test.web.servlet.MockMvc;

/** HU-27 · Tarea 195 (parte HTTP): contrato del historial y criterio 4, solo el contador autenticado (el estudio) lo ve. */
@WebMvcTest(controllers = HistorialController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class HistorialControllerTest {

    private static final String RUTA = "/api/tributario/clientes/7/historial";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean HistorialService service;

    private String bearer(String usuario, String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject(usuario).claim("roles", List.of(roles));
        return "Bearer " + token.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void criterio4_elContadorYElAdministradorVenElHistorialConTipoFechaYDescripcion() throws Exception {
        when(service.delCliente(7L)).thenReturn(List.of(new HistorialItemDto(Origen.SOLICITUD, 5L, "TRAMITE", LocalDate.of(2026, 11, 11),
                "Baja de RUC", false, "PENDIENTE", LocalDate.of(2026, 11, 20), LocalDateTime.of(2026, 11, 11, 10, 0))));

        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("u", rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].origen").value("SOLICITUD"))
                    .andExpect(jsonPath("$[0].tipo").value("TRAMITE"))
                    .andExpect(jsonPath("$[0].fecha").value("2026-11-11"))
                    .andExpect(jsonPath("$[0].descripcion").value("Baja de RUC"));
        }
    }

    @Test
    void unClienteInexistenteDa404() throws Exception {
        when(service.delCliente(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));

        mvc.perform(get("/api/tributario/clientes/99/historial").header(HttpHeaders.AUTHORIZATION, bearer("u", "CONTADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void criterio4_sinSesionDa401YUnClienteMypeOUnTokenSinRolDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("cliente1", "CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("nadie"))).andExpect(status().isForbidden());

        verify(service, never()).delCliente(any());
    }
}
