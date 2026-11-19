package com.pluscontable.tributario.compras;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.GlobalExceptionHandler;
import com.pluscontable.tributario.config.SecurityConfig;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-25 · T216 (permisos): el contador y el admin cargan, ven y validan; el cliente MYPE ve y propone la clasificación solo de las
 * compras de su empresa y no carga ni valida; el contador no propone; otra empresa recibe 403; sin sesión 401.
 */
@WebMvcTest(CompraController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CompraPermisosTest {
    private static final String RUTA = "/api/tributario/clientes/7/compras";
    private static final String COMPRA = "{\"periodo\":\"2026-11-01\",\"proveedorRuc\":\"20100070970\",\"comprobante\":\"F001-1\","
            + "\"fechaComprobante\":\"2026-11-05\",\"importe\":100.00}";
    private static final String CLASIFICACION = "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"Laptop\"}";

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean CompraService service;

    private static CompraDto compra() {
        return new CompraDto(3L, 7L, LocalDate.of(2026, 11, 1), "20100070970", null, "F001-1", LocalDate.of(2026, 11, 5), new BigDecimal("100.00"),
                ReglaClasificacionCompra.Estado.PROPUESTA, ReglaClasificacionCompra.Tipo.ACTIVO_FIJO, "Laptop", null, "contador1", null, true);
    }

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return "Bearer " + b.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void contadorYAdminCarganVenYValidanLasComprasDeCualquierCliente() throws Exception {
        when(service.validar(7L, 3L, "usuario-prueba")).thenReturn(compra());
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null)).contentType(MediaType.APPLICATION_JSON).content(COMPRA))
                    .andExpect(status().isCreated());
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null))).andExpect(status().isOk());
            mvc.perform(post(RUTA + "/3/validacion").header(HttpHeaders.AUTHORIZATION, bearer(rol, null))).andExpect(status().isOk());
        }
        verify(service, org.mockito.Mockito.times(2)).listar(7L, null);
        verify(service, org.mockito.Mockito.times(2)).validar(7L, 3L, "usuario-prueba");
    }

    @Test
    void elContadorNoProponeLaClasificacion() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, bearer(rol, null))
                    .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isForbidden());
        }
        verify(service, never()).proponer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void elClienteVeYProponeLasDeSuEmpresaPeroNoLasCargaNiLasValida() throws Exception {
        when(service.proponer(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(compra());
        String suyo = bearer("CLIENTE_MYPE", 7L);
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, suyo)).andExpect(status().isOk());
        mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, suyo)
                .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isOk());
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, suyo).contentType(MediaType.APPLICATION_JSON).content(COMPRA))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA + "/3/validacion").header(HttpHeaders.AUTHORIZATION, suyo)).andExpect(status().isForbidden());
        verify(service, never()).registrar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(service, never()).validar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void laAlertaYQuienValidoSonDelContadorYNoLlegaAlCliente() throws Exception {
        when(service.listar(7L, null)).thenReturn(List.of(compra()));
        when(service.proponer(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(compra());
        var json = org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].alertaCierre");

        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR", null)))
                .andExpect(json.value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].validadaPor").value("contador1"));
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L)))
                .andExpect(json.value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].validadaPor").doesNotExist());
        mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE_MYPE", 7L))
                        .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.alertaCierre").value(false));
    }

    @Test
    void elClienteNuncaVeNiProponeLasComprasDeOtraEmpresaNiSinEmpresa() throws Exception {
        for (String token : List.of(bearer("CLIENTE_MYPE", 8L), bearer("CLIENTE_MYPE", null))) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isForbidden());
        }
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(service, never()).proponer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(COMPRA)).andExpect(status().isUnauthorized());
        mvc.perform(patch(RUTA + "/3/clasificacion").contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA + "/3/validacion")).andExpect(status().isUnauthorized());
    }
}
