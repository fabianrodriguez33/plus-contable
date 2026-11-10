package com.pluscontable.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.config.SecurityConfig;
import com.pluscontable.auth.config.SesionToken;
import com.pluscontable.auth.dto.CuentaClienteResponse;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.dto.UserDto;
import com.pluscontable.auth.repository.TokenRevocadoRepository;
import com.pluscontable.auth.service.AuthService;
import com.pluscontable.auth.service.CuentaClienteService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-37 (tareas 14, 15 y 17): contrato HTTP de crear la cuenta de un cliente, del cambio obligatorio de la contraseña y del
 * token restringido del primer ingreso, con el filtro y las reglas de seguridad reales.
 */
@WebMvcTest(controllers = {CuentaClienteController.class, AuthController.class})
@Import({SecurityConfig.class, JwtProvider.class})
class CuentaClienteControllerTest {

    private static final String RUTA = "/api/auth/clientes/7/cuenta";
    private static final String CUERPO = "{\"correo\":\"contacto@acme.pe\"}";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockBean CuentaClienteService cuentas;
    @MockBean AuthService auth;
    @MockBean TokenRevocadoRepository revocados;

    private String bearer(String... roles) {
        return "Bearer " + jwt.generate("ana", List.of(roles));
    }

    private String restringido() {
        return "Bearer " + jwt.generateCambioPassword("contacto@acme.pe");
    }

    private MockHttpServletRequestBuilder crear(String autorizacion, String cuerpo) {
        var r = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private MockHttpServletRequestBuilder cambiar(String autorizacion) {
        var r = post("/api/auth/cambiar-password").contentType(MediaType.APPLICATION_JSON).content("{\"passwordNueva\":\"MiClaveNueva9\"}");
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return r;
    }

    private static final LoginResponse NORMAL = new LoginResponse("jwt-normal", "Bearer", 28800,
            new UserDto(9L, "contacto@acme.pe", "Acme SAC", List.of("CLIENTE_MYPE")), false);

    // ---- crear la cuenta

    @Test
    void elContadorCreaLaCuentaYRecibe201ConLaTemporalSinCache() throws Exception {
        when(cuentas.crear(eq(7L), eq("contacto@acme.pe"), anyString(), eq("ana"), anyString()))
                .thenReturn(new CuentaClienteResponse(9L, "contacto@acme.pe", 7L, "Temporal2345"));

        mvc.perform(crear(bearer("CONTADOR"), CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.usuarioId").value(9))
                .andExpect(jsonPath("$.correo").value("contacto@acme.pe"))
                .andExpect(jsonPath("$.clienteId").value(7))
                .andExpect(jsonPath("$.passwordTemporal").value("Temporal2345"));
    }

    @Test
    void alServicioLeLlegaElTokenDelContadorParaReenviarloYSuUsuarioParaLaBitacora() throws Exception {
        when(cuentas.crear(any(), any(), any(), any(), any())).thenReturn(new CuentaClienteResponse(9L, "c@a.pe", 7L, "x"));
        String token = bearer("ADMIN");

        mvc.perform(crear(token, CUERPO)).andExpect(status().isCreated());

        verify(cuentas).crear(eq(7L), eq("contacto@acme.pe"), eq(token), eq("ana"), anyString());
    }

    @Test
    void sinSesionDa401ConUnClienteDa403SinLlamarAlServicio() throws Exception {
        mvc.perform(crear(null, CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(crear(bearer("CLIENTE_MYPE"), CUERPO)).andExpect(status().isForbidden());

        verify(cuentas, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    void unTokenSinRolesDa403() throws Exception {
        mvc.perform(crear("Bearer " + jwt.generate("ana", List.of()), CUERPO)).andExpect(status().isForbidden());

        verify(cuentas, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    void elTokenRestringidoDelPrimerIngresoNoCreaCuentas() throws Exception {
        mvc.perform(crear(restringido(), CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Debe cambiar la contraseña temporal antes de usar el sistema"));

        verify(cuentas, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    void losErroresDelServicioSeDevuelvenConSuEstadoYSuMensaje() throws Exception {
        when(cuentas.crear(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan o son inválidos: correo"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado: 7"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "La empresa ya tiene una cuenta de acceso"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comprobar el cliente"));

        mvc.perform(crear(bearer("CONTADOR"), CUERPO)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("Faltan o son inválidos: correo"));
        mvc.perform(crear(bearer("CONTADOR"), CUERPO)).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value("Cliente no encontrado: 7"));
        mvc.perform(crear(bearer("CONTADOR"), CUERPO)).andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje").value("La empresa ya tiene una cuenta de acceso"));
        mvc.perform(crear(bearer("CONTADOR"), CUERPO)).andExpect(status().isBadGateway());
    }

    @Test
    void sinCuerpoElServicioRecibeUnCorreoNuloYResponde400() throws Exception {
        when(cuentas.crear(any(), isNull(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan o son inválidos: correo"));

        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(crear(bearer("CONTADOR"), "no es json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud no es válido"));
    }

    // ---- cambio obligatorio y token restringido

    @Test
    void conElTokenRestringidoSeCambiaLaContraseniaYSeRecibeElTokenNormal() throws Exception {
        when(auth.cambiarPassword(eq("contacto@acme.pe"), any(SesionToken.class), eq("MiClaveNueva9"), anyString())).thenReturn(NORMAL);

        mvc.perform(cambiar(restringido()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.token").value("jwt-normal"))
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));
    }

    @Test
    void alServicioLeLlegaSiElTokenEraElRestringidoOUnoNormal() throws Exception {
        when(auth.cambiarPassword(any(), any(), any(), any())).thenReturn(NORMAL);

        mvc.perform(cambiar(restringido())).andExpect(status().isOk());
        mvc.perform(cambiar(bearer("CLIENTE_MYPE"))).andExpect(status().isOk());

        ArgumentCaptor<SesionToken> c = ArgumentCaptor.forClass(SesionToken.class);
        verify(auth, org.mockito.Mockito.times(2)).cambiarPassword(any(), c.capture(), any(), any());
        assertThat(c.getAllValues().get(0).cambiarPassword()).isTrue();
        assertThat(c.getAllValues().get(0).jti()).isNotBlank();
        assertThat(c.getAllValues().get(1).cambiarPassword()).isFalse();
    }

    @Test
    void cambiarLaContraseniaSinSesionDa401() throws Exception {
        mvc.perform(cambiar(null)).andExpect(status().isUnauthorized());

        verify(auth, never()).cambiarPassword(any(), any(), any(), any());
    }

    @Test
    void losErroresDelCambioSeDevuelvenConSuMensaje() throws Exception {
        when(auth.cambiarPassword(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña nueva no puede ser la temporal"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene un cambio de contraseña pendiente"));

        mvc.perform(cambiar(restringido())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La contraseña nueva no puede ser la temporal"));
        mvc.perform(cambiar(bearer("CLIENTE_MYPE"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene un cambio de contraseña pendiente"));
    }

    @Test
    void conElTokenRestringidoNoSeUsaNingunaOtraRutaDeMsAuth() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, restringido()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Debe cambiar la contraseña temporal antes de usar el sistema"));
        mvc.perform(get("/api/auth/otra-ruta").header(HttpHeaders.AUTHORIZATION, restringido()))
                .andExpect(status().isForbidden());

        verify(auth, never()).me(any());
    }

    @Test
    void conElTokenRestringidoSiSePuedeCerrarSesion() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, restringido()))
                .andExpect(status().isNoContent());

        verify(auth).logout(eq("contacto@acme.pe"), anyString(), any(), anyString());
    }

    @Test
    void unTokenNormalSiUsaMeComoSiempre() throws Exception {
        when(auth.me("ana")).thenReturn(new UserDto(1L, "ana", "Ana", List.of("CONTADOR")));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("CONTADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void elTokenRestringidoNoLlevaRolesNiEmpresa() {
        var claims = jwt.parse(jwt.generateCambioPassword("contacto@acme.pe"));

        assertThat(claims.get("roles", List.class)).isEmpty();
        assertThat(claims.get("cambiarPassword", Boolean.class)).isTrue();
        assertThat(claims.containsKey("clienteId")).isFalse();
    }
}
