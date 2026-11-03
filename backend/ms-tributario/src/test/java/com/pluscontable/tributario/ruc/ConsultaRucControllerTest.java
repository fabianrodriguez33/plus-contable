package com.pluscontable.tributario.ruc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.ValidationException;
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

/** HU-01: solo el estudio consulta el RUC; el RUC mal formado es 400 y el resto de resultados es 200. */
@WebMvcTest(controllers = ConsultaRucController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ConsultaRucControllerTest {

    private static final String RUTA = "/api/tributario/ruc/20123456786/consulta";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean ConsultaRucService service;

    private String bearer(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void elContadorRecibeLaSugerencia() throws Exception {
        var sugerencia = new ConsultaRucDto.Sugerencia("20123456786", "EMPRESA SAC", "AV. UNO 1", null, "ACTIVO",
                "HABIDO", "150101", "SUNAT", null);
        when(service.consultar("20123456786")).thenReturn(new ConsultaRucDto(ConsultaRucDto.Resultado.ENCONTRADO,
                "Datos sugeridos.", sugerencia, List.of()));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ENCONTRADO"))
                .andExpect(jsonPath("$.datos.razonSocial").value("EMPRESA SAC"));
    }

    @Test
    void elAdministradorTambienPuede() throws Exception {
        when(service.consultar("20123456786")).thenReturn(new ConsultaRucDto(ConsultaRucDto.Resultado.NO_DISPONIBLE,
                "No disponible.", null, List.of()));

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("NO_DISPONIBLE"));
    }

    @Test
    void elClienteMypeNoPuedeConsultar() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());
        verify(service, never()).consultar(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unTokenSinRolRecibe403YSinTokenRecibe401() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    @Test
    void unRucMalFormadoEsBadRequestConElMotivo() throws Exception {
        when(service.consultar("123")).thenThrow(new ValidationException("El RUC debe tener exactamente 11 dígitos numéricos"));

        mvc.perform(get("/api/tributario/ruc/123/consulta").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El RUC debe tener exactamente 11 dígitos numéricos"));
    }
}
