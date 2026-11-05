package com.pluscontable.tributario.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pluscontable.tributario.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** HT-01: el endpoint de salud responde sin sesión y no abre el resto de la API. */
@WebMvcTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void respondeUpSinSesion() throws Exception {
        mvc.perform(get("/api/tributario/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.servicio").value("ms-tributario"));
    }

    @Test
    void elRestoDeLaApiSigueProtegida() throws Exception {
        mvc.perform(get("/api/tributario/clientes")).andExpect(status().isUnauthorized());
    }
}
