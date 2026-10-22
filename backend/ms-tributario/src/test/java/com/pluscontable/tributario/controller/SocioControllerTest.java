package com.pluscontable.tributario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import com.pluscontable.tributario.service.SocioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
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

/** HU-02: validaciones de entrada y forma de la respuesta de /socios, con JWT firmados como ms-auth. */
@WebMvcTest(controllers = SocioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SocioControllerTest {

    private static final String BASE = "/api/tributario/clientes/1/socios";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean SocioService socioService;
    @MockBean PatrimonioLaboralService patrimonioService;

    private String bearer(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject("u").claim("roles", List.of(rol))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private static final String SOCIOS_OK = """
            [{"nombreCompleto":"Ana","numeroDocumento":"1","aporte":6000,"porcentaje":60},
             {"nombreCompleto":"Luis","numeroDocumento":"2","aporte":4000,"porcentaje":40}]""";

    @Test
    void elResumenDevuelveCapitalSocialTotalYDiferencia() throws Exception {
        when(socioService.resumen(1L)).thenReturn(new ResumenSocietarioDto(
                List.of(new SocioDto(1L, "Ana", "DNI", "1", new BigDecimal("6000.00"), new BigDecimal("60.00"), null)),
                new BigDecimal("6000.00"), new BigDecimal("60.00"), new BigDecimal("40.00"), false));

        mvc.perform(get(BASE + "/resumen").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalSocialTotal").value(6000.00))
                .andExpect(jsonPath("$.porcentajeTotal").value(60.00))
                .andExpect(jsonPath("$.diferencia").value(40.00))
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.socios[0].aporte").value(6000.00));
    }

    @Test
    void guardarSociosSinAporteSeRechazaConMensajeClaro() throws Exception {
        String sinAporte = "[{\"nombreCompleto\":\"Ana\",\"numeroDocumento\":\"1\",\"porcentaje\":100}]";

        mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(sinAporte))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("El aporte es obligatorio")));
        verify(socioService, never()).reemplazar(anyLong(), any());
    }

    @Test
    void guardarSociosConAporteNegativoSeRechaza() throws Exception {
        String negativo = "[{\"nombreCompleto\":\"Ana\",\"numeroDocumento\":\"1\",\"aporte\":-1,\"porcentaje\":100}]";

        mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(negativo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no puede ser negativo")));
    }

    @Test
    void sumaDistintaDe100DevuelveElMensajeConLaDiferencia() throws Exception {
        when(socioService.reemplazar(anyLong(), any()))
                .thenThrow(new ValidationException("La suma de participaciones debe ser exactamente 100.00%: actual 90.00%, faltan 10.00% para llegar a 100.00%"));

        mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(SOCIOS_OK))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("faltan 10.00%")));
    }

    @Test
    void editarElAporteValidaElMontoYDevuelveElResumenActualizado() throws Exception {
        when(socioService.actualizarAporte(1L, 5L, new BigDecimal("7500")))
                .thenReturn(new ResumenSocietarioDto(List.of(), new BigDecimal("7500.00"), new BigDecimal("100.00"),
                        BigDecimal.ZERO, true));

        mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aporte\":7500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalSocialTotal").value(7500.00))
                .andExpect(jsonPath("$.completo").value(true));

        mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aporte\":-5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElEstudioAccedeALosSocios() throws Exception {
        mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/resumen").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden());
        mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"aporte\":1}")).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
