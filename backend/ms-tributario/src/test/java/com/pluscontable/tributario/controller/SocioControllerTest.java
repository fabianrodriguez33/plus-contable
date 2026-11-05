package com.pluscontable.tributario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pluscontable.tributario.dto.SocioEntradaDto;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import com.pluscontable.tributario.service.SocioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Test
    void elResumenDevuelveCapitalSocialTotalYDiferencia() throws Exception {
        when(socioService.resumen(1L)).thenReturn(new ResumenSocietarioDto(
                List.of(new SocioDto(1L, "Ana", "DNI", "1", new BigDecimal("6000.00"), new BigDecimal("60.00"), null)),
                new BigDecimal("6000.00"), new BigDecimal("60.00"), new BigDecimal("40.00"), false, 0));

        mvc.perform(get(BASE + "/resumen").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalSocialTotal").value(6000.00))
                .andExpect(jsonPath("$.porcentajeTotal").value(60.00))
                .andExpect(jsonPath("$.diferencia").value(40.00))
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.sociosSinAporte").value(0))
                .andExpect(jsonPath("$.socios[0].aporte").value(6000.00))
                .andExpect(jsonPath("$.socios[0].porcentaje").value(60.00));
    }

    @Test
    void guardarSociosSinAporteSeRechazaConMensajeClaro() throws Exception {
        String sinAporte = "[{\"nombreCompleto\":\"Ana\",\"numeroDocumento\":\"1\"}]";

        mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(sinAporte))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("El aporte es obligatorio")));
        verify(socioService, never()).reemplazar(anyLong(), any());
    }

    @Test
    void guardarSociosConAporteNegativoOEnCeroSeRechaza() throws Exception {
        for (String monto : List.of("-1", "0")) {
            String body = "[{\"nombreCompleto\":\"Ana\",\"numeroDocumento\":\"1\",\"aporte\":" + monto + "}]";

            mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("El aporte debe ser mayor a 0")));
        }
        verify(socioService, never()).reemplazar(anyLong(), any());
    }

    @Test
    void elPorcentajeQueEnvieElClienteSeIgnoraPorqueLoCalculaElSistema() throws Exception {
        String conPorcentaje = "[{\"nombreCompleto\":\"Ana\",\"numeroDocumento\":\"1\",\"aporte\":6000,\"porcentaje\":5}]";
        when(socioService.reemplazar(anyLong(), any())).thenReturn(new ResumenSocietarioDto(List.of(),
                BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO, true, 0));

        mvc.perform(put(BASE).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(conPorcentaje))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SocioEntradaDto>> enviado = ArgumentCaptor.forClass(List.class);
        verify(socioService).reemplazar(eq(1L), enviado.capture());
        assertThat(enviado.getValue()).singleElement().satisfies(s -> assertThat(s.aporte()).isEqualByComparingTo("6000"));
    }

    @Test
    void editarElAporteValidaElMontoYDevuelveElResumenActualizado() throws Exception {
        when(socioService.actualizarAporte(1L, 5L, new BigDecimal("7500")))
                .thenReturn(new ResumenSocietarioDto(List.of(), new BigDecimal("7500.00"), new BigDecimal("100.00"),
                        BigDecimal.ZERO, true, 0));

        mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aporte\":7500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalSocialTotal").value(7500.00))
                .andExpect(jsonPath("$.completo").value(true));

        for (String invalido : List.of("{\"aporte\":-5}", "{\"aporte\":0}", "{}")) {
            mvc.perform(patch(BASE + "/5/aporte").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(invalido))
                    .andExpect(status().isBadRequest());
        }
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
