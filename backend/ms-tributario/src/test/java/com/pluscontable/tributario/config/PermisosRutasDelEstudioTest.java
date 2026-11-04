package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.pluscontable.tributario.controller.ClienteController;
import com.pluscontable.tributario.controller.SocioController;
import com.pluscontable.tributario.service.ClienteService;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import com.pluscontable.tributario.service.SocioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-38 · Tarea 93: las rutas de la matriz que no tenían prueba de rol propia (cliente por id, clave SOL,
 * alta y reemplazo de socios, activos fijos). Solo el estudio entra; es el mismo patrón de {@link PermisosPorRolTest}.
 */
@WebMvcTest(controllers = {ClienteController.class, SocioController.class})
@Import(SecurityConfig.class)
class PermisosRutasDelEstudioTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secreto;

    @MockBean ClienteService clienteService;
    @MockBean SocioService socioService;
    @MockBean PatrimonioLaboralService patrimonioService;

    /** Método y ruta: todas son solo del estudio. */
    static Stream<Arguments> rutas() {
        return Stream.of(
                Arguments.of("GET", "/api/tributario/clientes/1"),
                Arguments.of("PUT", "/api/tributario/clientes/1"),
                Arguments.of("GET", "/api/tributario/clientes/1/sol"),
                Arguments.of("PUT", "/api/tributario/clientes/1/sol"),
                Arguments.of("PUT", "/api/tributario/clientes/1/socios"),
                Arguments.of("POST", "/api/tributario/clientes/1/socios"),
                Arguments.of("GET", "/api/tributario/clientes/1/activos"),
                Arguments.of("POST", "/api/tributario/clientes/1/activos"),
                Arguments.of("DELETE", "/api/tributario/clientes/1/activos/7"));
    }

    private String token(List<String> roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("usuario").claim("roles", roles)
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private int estado(String metodo, String ruta, String token) throws Exception {
        MockHttpServletRequestBuilder r = request(HttpMethod.valueOf(metodo), ruta)
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (token != null) r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(r).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "CONTADOR {0} {1}")
    @MethodSource("rutas")
    void contadorPasaLaSeguridad(String metodo, String ruta) throws Exception {
        // lo que responda después (200, 400…) ya es del controlador
        assertThat(estado(metodo, ruta, token(List.of("CONTADOR")))).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "ADMIN {0} {1}")
    @MethodSource("rutas")
    void adminPasaLaSeguridad(String metodo, String ruta) throws Exception {
        assertThat(estado(metodo, ruta, token(List.of("ADMIN")))).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "CLIENTE_MYPE {0} {1}")
    @MethodSource("rutas")
    void clienteMypeRecibe403(String metodo, String ruta) throws Exception {
        assertThat(estado(metodo, ruta, token(List.of("CLIENTE_MYPE")))).isEqualTo(403);
    }

    @ParameterizedTest(name = "sin rol {0} {1}")
    @MethodSource("rutas")
    void tokenSinRolRecibe403(String metodo, String ruta) throws Exception {
        assertThat(estado(metodo, ruta, token(List.of()))).isEqualTo(403);
    }

    @ParameterizedTest(name = "sin token {0} {1}")
    @MethodSource("rutas")
    void sinTokenRecibe401(String metodo, String ruta) throws Exception {
        assertThat(estado(metodo, ruta, null)).isEqualTo(401);
    }
}
