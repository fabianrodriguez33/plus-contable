package com.pluscontable.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluscontable.auth.service.ClienteTributarioClient;
import com.pluscontable.auth.service.ClienteTributarioClient.ClienteResumen;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HU-41 (tareas 228 y 231): el cliente que olvidó su contraseña la pide desde el login, el estudio la ve y le restablece el
 * acceso, y la contraseña anterior deja de servir; de punta a punta con una base PostgreSQL real (desechable) y los roles reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestablecimientoIntegracionTest {

    private static final long CLIENTE = 987_101L;
    private static final String CORREO = "itest-hu41-a@acme.pe";
    private static final String PROPIA = "MiClavePropia9";
    private static final String NUEVA = "OtraClaveNueva7";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockBean ClienteTributarioClient tributario;
    @Value("${app.seed.admin-username}") String admin;
    @Value("${app.seed.admin-password}") String claveAdmin;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void preparar() {
        when(tributario.buscar(eq(CLIENTE), any())).thenReturn(Optional.of(new ClienteResumen(CLIENTE, "Acme SAC")));
        limpiar();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auth_schema.tokens_revocados WHERE username LIKE 'itest-hu41-%'");
        jdbc.update("DELETE FROM auth_schema.bitacora_seguridad WHERE username LIKE 'itest-hu41-%' OR detalle LIKE '%itest-hu41-%'");
        jdbc.update("DELETE FROM auth_schema.usuarios WHERE username LIKE 'itest-hu41-%'"); // sus solicitudes, en cascada
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult entrar(String usuario, String clave, int estado) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", usuario, "password", clave)))).andExpect(status().is(estado)).andReturn();
    }

    private String contador() throws Exception {
        return "Bearer " + cuerpo(entrar(admin, claveAdmin, 200)).get("token").asText();
    }

    private String cambiar(String tokenRestringido, String nueva) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRestringido)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("passwordNueva", nueva))))
                .andExpect(status().isOk()).andReturn();
        return cuerpo(r).get("token").asText();
    }

    /** Un cliente con cuenta que ya cambió la temporal por su propia contraseña; devuelve su token normal. */
    private String clienteConSuPropiaClave(String contador) throws Exception {
        MvcResult creada = mvc.perform(post("/api/auth/clientes/" + CLIENTE + "/cuenta").header(HttpHeaders.AUTHORIZATION, contador)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo", CORREO))))
                .andExpect(status().isCreated()).andReturn();
        String temporal = cuerpo(creada).get("passwordTemporal").asText();
        return cambiar(cuerpo(entrar(CORREO, temporal, 200)).get("token").asText(), PROPIA);
    }

    private MvcResult solicitar(String correo) throws Exception {
        return mvc.perform(post("/api/auth/restablecimiento/solicitar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo)))).andExpect(status().isOk()).andReturn();
    }

    private JsonNode pendientes(String contador) throws Exception {
        return cuerpo(mvc.perform(get("/api/auth/restablecimiento/pendientes").header(HttpHeaders.AUTHORIZATION, contador))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode pendienteDe(JsonNode lista) {
        for (JsonNode s : lista) if (s.get("clienteId").asLong() == CLIENTE) return s;
        return null;
    }

    @Test
    void recorridoCompletoElClientePideElEstudioRestableceYLaAnteriorDejaDeServir() throws Exception {
        String contador = contador();
        clienteConSuPropiaClave(contador);

        // criterio 1: el cliente lo pide sin sesión y el estudio ve la solicitud
        solicitar(CORREO.toUpperCase());
        JsonNode pendiente = pendienteDe(pendientes(contador));
        assertThat(pendiente).isNotNull();
        assertThat(pendiente.get("correo").asText()).isEqualTo(CORREO);
        solicitar(CORREO); // pedirlo otra vez no duplica
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_schema.solicitudes_restablecimiento s JOIN auth_schema.usuarios u "
                + "ON u.id = s.usuario_id WHERE u.username = ?", Integer.class, CORREO)).isEqualTo(1);

        // el estudio restablece: la solicitud queda atendida y el cliente recibe el medio (otra temporal)
        MvcResult r = mvc.perform(post("/api/auth/clientes/" + CLIENTE + "/cuenta/restablecer").header(HttpHeaders.AUTHORIZATION, contador))
                .andExpect(status().isOk()).andReturn();
        String temporal = cuerpo(r).get("passwordTemporal").asText();
        assertThat(pendienteDe(pendientes(contador))).isNull();
        assertThat(jdbc.queryForObject("SELECT atendida_por FROM auth_schema.solicitudes_restablecimiento s JOIN auth_schema.usuarios u "
                + "ON u.id = s.usuario_id WHERE u.username = ?", String.class, CORREO)).isEqualTo(admin);

        // criterio 3: la contraseña anterior ya no sirve; con la temporal entra, la cambia y la temporal tampoco sirve
        entrar(CORREO, PROPIA, 401);
        String restringido = cuerpo(entrar(CORREO, temporal, 200)).get("token").asText();
        cambiar(restringido, NUEVA);
        entrar(CORREO, temporal, 401);
        entrar(CORREO, PROPIA, 401);
        entrar(CORREO, NUEVA, 200);
    }

    @Test
    void criterio2_unCorreoSinCuentaRecibeLaMismaRespuestaQueUnoRegistradoYNoCreaNada() throws Exception {
        String contador = contador();
        clienteConSuPropiaClave(contador);
        int antes = jdbc.queryForObject("SELECT count(*) FROM auth_schema.solicitudes_restablecimiento", Integer.class);

        String sinCuenta = solicitar("itest-hu41-nadie@acme.pe").getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_schema.solicitudes_restablecimiento", Integer.class)).isEqualTo(antes);
        String conCuenta = solicitar(CORREO).getResponse().getContentAsString();

        assertThat(sinCuenta).isEqualTo(conCuenta).contains("Si el correo corresponde a una cuenta de cliente");
        // la cuenta del estudio tampoco genera solicitud
        solicitar(admin);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_schema.solicitudes_restablecimiento WHERE atendida_en IS NULL "
                + "AND usuario_id = (SELECT id FROM auth_schema.usuarios WHERE username = ?)", Integer.class, admin)).isZero();
    }

    @Test
    void soloElEstudioVeLasSolicitudesYUnCorreoVacioDa400() throws Exception {
        String contador = contador();
        String tokenCliente = clienteConSuPropiaClave(contador);

        mvc.perform(get("/api/auth/restablecimiento/pendientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/restablecimiento/pendientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/restablecimiento/solicitar").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laTablaAdmiteUnaSolaPendientePorCuentaYExigeQuienYCuandoAlAtender() throws Exception {
        clienteConSuPropiaClave(contador());
        long usuario = jdbc.queryForObject("SELECT id FROM auth_schema.usuarios WHERE username = ?", Long.class, CORREO);
        String insertar = "INSERT INTO auth_schema.solicitudes_restablecimiento (usuario_id) VALUES (?)";

        jdbc.update(insertar, usuario);
        assertThatThrownBy(() -> jdbc.update(insertar, usuario)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE auth_schema.solicitudes_restablecimiento SET atendida_en = NOW() WHERE usuario_id = ?", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE auth_schema.solicitudes_restablecimiento SET atendida_en = NOW(), atendida_por = 'x' WHERE usuario_id = ?", usuario);
        jdbc.update(insertar, usuario); // atendida la anterior, se puede pedir de nuevo
    }
}
