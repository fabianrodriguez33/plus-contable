package com.pluscontable.tributario.compras;

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
 * HU-25 · T216 (permisos): el contador y el admin cargan y ven; el cliente MYPE ve y clasifica solo las compras de su empresa y no
 * carga; el contador no clasifica; otra empresa recibe 403; sin sesión 401.
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

    private String bearer(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return "Bearer " + b.expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    @Test
    void contadorYAdminCarganYVenLasComprasDeCualquierCliente() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null)).contentType(MediaType.APPLICATION_JSON).content(COMPRA))
                    .andExpect(status().isCreated());
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(rol, null))).andExpect(status().isOk());
        }
        verify(service, org.mockito.Mockito.times(2)).listar(7L, null);
    }

    @Test
    void elContadorNoClasifica() throws Exception {
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, bearer(rol, null))
                    .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isForbidden());
        }
        verify(service, never()).clasificar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void elClienteVeYClasificaLasDeSuEmpresaPeroNoLasCarga() throws Exception {
        String suyo = bearer("CLIENTE_MYPE", 7L);
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, suyo)).andExpect(status().isOk());
        mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, suyo)
                .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isOk());
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, suyo).contentType(MediaType.APPLICATION_JSON).content(COMPRA))
                .andExpect(status().isForbidden());
        verify(service, never()).registrar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void elClienteNuncaVeNiClasificaLasComprasDeOtraEmpresaNiSinEmpresa() throws Exception {
        for (String token : List.of(bearer("CLIENTE_MYPE", 8L), bearer("CLIENTE_MYPE", null))) {
            mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
            mvc.perform(patch(RUTA + "/3/clasificacion").header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isForbidden());
        }
        verify(service, never()).listar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(service, never()).clasificar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(COMPRA)).andExpect(status().isUnauthorized());
        mvc.perform(patch(RUTA + "/3/clasificacion").contentType(MediaType.APPLICATION_JSON).content(CLASIFICACION)).andExpect(status().isUnauthorized());
    }
}
