package com.pluscontable.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.config.SecurityConfig;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.dto.UserDto;
import com.pluscontable.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** HU-36 / HT-03: contrato HTTP del login y filtro JWT de ms-auth (token vigente, vencido, alterado y ausente). */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtProvider.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean AuthService authService;

    private static final UserDto ANA = new UserDto(1L, "ana", "Ana Pérez", List.of("CONTADOR"));

    private String login(String cuerpo, int estado) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().is(estado)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void loginCorrectoDevuelveElTokenYElUsuario() throws Exception {
        when(authService.login(any(), any())).thenReturn(new LoginResponse("jwt-de-prueba", "Bearer", 28800, ANA));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"Clave123*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-de-prueba"))
                .andExpect(jsonPath("$.usuario.roles[0]").value("CONTADOR"));
    }

    @Test
    void credencialesIncorrectasDan401ConMensajeGenerico() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos"));

        assertThat(login("{\"username\":\"ana\",\"password\":\"mala\"}", 401))
                .contains("Usuario o contraseña incorrectos");
    }

    @Test
    void cuentaBloqueadaDa423() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.LOCKED, "Cuenta bloqueada por intentos fallidos"));

        assertThat(login("{\"username\":\"ana\",\"password\":\"x\"}", 423)).contains("Cuenta bloqueada");
    }

    @Test
    void sinUsuarioOSinContrasenaDa400SinLlamarAlServicio() throws Exception {
        assertThat(login("{\"username\":\"\",\"password\":\"x\"}", 400)).contains("obligatorios");
        assertThat(login("{\"username\":\"ana\"}", 400)).contains("obligatorios");
        verify(authService, never()).login(any(), any());
    }

    @Test
    void meConTokenVigenteResponde200() throws Exception {
        when(authService.me("ana")).thenReturn(ANA);
        String token = jwt.generate("ana", List.of("CONTADOR"));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void meSinTokenDa401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meConTokenVencidoDa401() throws Exception {
        String vencido = new JwtProvider(secreto, -1)
                .generate("ana", List.of("CONTADOR"));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meConTokenAlteradoOFirmadoPorOtroDa401() throws Exception {
        String ajeno = new JwtProvider("clave-ajena-que-no-es-la-del-estudio-32-bytes", 8)
                .generate("ana", List.of("ADMIN"));
        String[] p = jwt.generate("ana", List.of("CONTADOR")).split("\\.");
        String alterado = p[0] + "." + p[1].substring(0, 4) + (p[1].charAt(4) == 'A' ? 'B' : 'A') + p[1].substring(5)
                + "." + p[2];

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + alterado))
                .andExpect(status().isUnauthorized());
    }
}
