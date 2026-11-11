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
import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.service.ClienteTributarioClient;
import com.pluscontable.auth.service.ClienteTributarioClient.ClienteResumen;
import io.jsonwebtoken.Claims;
import java.util.List;
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
 * HU-37 (tarea 17): crear la cuenta de un cliente, su primer ingreso y el cambio obligatorio de la contraseña de punta a punta,
 * con el servicio completo y una base PostgreSQL real (desechable). Solo ms-tributario se simula (no hay otro servicio).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CuentaClienteIntegracionTest {

    private static final long CLIENTE = 987_001L;
    private static final long OTRO_CLIENTE = 987_002L;
    private static final String CORREO = "itest-hu37-a@acme.pe";
    private static final String NUEVA = "MiClaveNueva9";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @MockBean ClienteTributarioClient tributario;
    @Value("${app.seed.admin-username}") String admin;
    @Value("${app.seed.admin-password}") String claveAdmin;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void simularTributario() {
        when(tributario.buscar(eq(CLIENTE), any())).thenReturn(Optional.of(new ClienteResumen(CLIENTE, "Acme SAC")));
        when(tributario.buscar(eq(OTRO_CLIENTE), any())).thenReturn(Optional.of(new ClienteResumen(OTRO_CLIENTE, "Beta SAC")));
        limpiar();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auth_schema.tokens_revocados WHERE username LIKE 'itest-hu37-%'");
        jdbc.update("DELETE FROM auth_schema.bitacora_seguridad WHERE username LIKE 'itest-hu37-%' OR detalle LIKE '%itest-hu37-%'");
        jdbc.update("DELETE FROM auth_schema.usuarios WHERE username LIKE 'itest-hu37-%'");
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String contador() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", admin, "password", claveAdmin)))).andExpect(status().isOk()).andReturn();
        return "Bearer " + cuerpo(r).get("token").asText();
    }

    private MvcResult crear(String autorizacion, long cliente, String correo, int estado) throws Exception {
        var r = post("/api/auth/clientes/" + cliente + "/cuenta").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo)));
        if (autorizacion != null) r.header(HttpHeaders.AUTHORIZATION, autorizacion);
        return mvc.perform(r).andExpect(status().is(estado)).andReturn();
    }

    private MvcResult entrar(String correo, String clave, int estado) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", correo, "password", clave)))).andExpect(status().is(estado)).andReturn();
    }

    private int estadoDe(String ruta, String token) throws Exception {
        return mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private MvcResult cambiar(String tokenRestringido, String nueva, int estado) throws Exception {
        return mvc.perform(post("/api/auth/cambiar-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRestringido)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("passwordNueva", nueva))))
                .andExpect(status().is(estado)).andReturn();
    }

    private int filasDeLaEmpresa(long cliente) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_schema.usuarios WHERE cliente_id = ?", Integer.class, cliente);
    }

    // ---- la llamada a ms-tributario va fuera de la transacción (HU-37)

    @Test
    void laConsultaAMsTributarioNoSeHaceDentroDeUnaTransaccion() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean dentro = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.when(tributario.buscar(org.mockito.ArgumentMatchers.eq(CLIENTE), org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            dentro.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return java.util.Optional.of(new ClienteResumen(CLIENTE, "Acme SAC"));
        });

        crear(contador(), CLIENTE, CORREO, 201);

        assertThat(dentro.get()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_schema.usuarios WHERE username = ?", Integer.class, CORREO)).isEqualTo(1);
    }

    // ---- vigencias (HU-37): la temporal 48 horas y el token restringido 15 minutos, tomadas de application.yml

    @Test
    void laTemporalVenceALas48HorasYElTokenRestringidoDuraQuinceMinutos() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();

        java.time.LocalDateTime vence = jdbc.queryForObject(
                "SELECT temporal_vence_en FROM auth_schema.usuarios WHERE username = ?", java.time.LocalDateTime.class, CORREO);
        assertThat(vence).isBetween(java.time.LocalDateTime.now().plusHours(47), java.time.LocalDateTime.now().plusHours(48).plusMinutes(1));
        JsonNode primero = cuerpo(entrar(CORREO, temporal, 200));
        assertThat(primero.get("expiraEnSegundos").asLong()).isEqualTo(15 * 60);
        long segundos = java.time.Duration.between(java.time.Instant.now(), jwt.parse(primero.get("token").asText()).getExpiration().toInstant()).toSeconds();
        assertThat(segundos).isBetween(14 * 60L, 15 * 60L);
    }

    @Test
    void conLaTemporalVencidaNoSeEntraYAlVolverASerVigenteSi() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();

        jdbc.update("UPDATE auth_schema.usuarios SET temporal_vence_en = NOW() - INTERVAL '1 minute' WHERE username = ?", CORREO);
        assertThat(entrar(CORREO, temporal, 401).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("venció");
        jdbc.update("UPDATE auth_schema.usuarios SET temporal_vence_en = NOW() + INTERVAL '1 hour' WHERE username = ?", CORREO);
        entrar(CORREO, temporal, 200);
    }

    // ---- el recorrido completo

    @Test
    void crearEntrarConLaTemporalCambiarlaYEntrarConLaNueva() throws Exception {
        String token = contador();

        JsonNode creada = cuerpo(crear(token, CLIENTE, "ITEST-hu37-A@Acme.pe ", 201));
        String temporal = creada.get("passwordTemporal").asText();
        assertThat(creada.get("correo").asText()).isEqualTo(CORREO);
        assertThat(creada.get("clienteId").asLong()).isEqualTo(CLIENTE);
        assertThat(temporal).hasSize(12);

        // primer ingreso: token restringido
        JsonNode primero = cuerpo(entrar(CORREO, temporal, 200));
        assertThat(primero.get("debeCambiarPassword").asBoolean()).isTrue();
        String restringido = primero.get("token").asText();
        Claims c = jwt.parse(restringido);
        assertThat(c.get("roles", List.class)).isEmpty();
        assertThat(estadoDe("/api/auth/me", restringido)).isEqualTo(403);
        crear("Bearer " + restringido, OTRO_CLIENTE, "itest-hu37-b@acme.pe", 403);

        // entrar no gasta la temporal: si el cliente se cortó antes del cambio, vuelve a entrar con la misma
        assertThat(jdbc.queryForObject("SELECT temporal_usada_en FROM auth_schema.usuarios WHERE username = ?", java.sql.Timestamp.class, CORREO)).isNull();
        JsonNode reingreso = cuerpo(entrar(CORREO, temporal, 200));
        assertThat(reingreso.get("debeCambiarPassword").asBoolean()).isTrue();
        assertThat(reingreso.get("token").asText()).isNotEqualTo(restringido);

        // cambio obligatorio: entrega el token normal y revoca el restringido
        JsonNode cambio = cuerpo(cambiar(restringido, NUEVA, 200));
        assertThat(cambio.get("debeCambiarPassword").asBoolean()).isFalse();
        String normal = cambio.get("token").asText();
        Claims n = jwt.parse(normal);
        assertThat(n.get("roles", List.class)).containsExactly("CLIENTE_MYPE");
        assertThat(n.get("clienteId", Long.class)).isEqualTo(CLIENTE);
        assertThat(estadoDe("/api/auth/me", normal)).isEqualTo(200);
        assertThat(estadoDe("/api/auth/me", restringido)).isEqualTo(401); // revocado
        cambiar(restringido, "OtraClave123", 401);

        // la temporal ya no sirve (se gastó al cambiarla) y la nueva sí
        entrar(CORREO, temporal, 401);
        assertThat(jdbc.queryForObject("SELECT temporal_usada_en FROM auth_schema.usuarios WHERE username = ?", java.sql.Timestamp.class, CORREO)).isNotNull();
        JsonNode despues = cuerpo(entrar(CORREO, NUEVA, 200));
        assertThat(despues.get("debeCambiarPassword").asBoolean()).isFalse();
        assertThat(jwt.parse(despues.get("token").asText()).get("clienteId", Long.class)).isEqualTo(CLIENTE);
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM auth_schema.usuarios WHERE username = ?", Boolean.class, CORREO)).isFalse();
    }

    @Test
    void laContraseniaNoQuedaEnClaroEnNingunLugarDeLaBase() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();
        String restringido = cuerpo(entrar(CORREO, temporal, 200)).get("token").asText();
        cambiar(restringido, NUEVA, 200);

        List<String> fila = jdbc.queryForList(
                "SELECT row(u.*)::text FROM auth_schema.usuarios u WHERE u.username = ?", String.class, CORREO);
        assertThat(fila).hasSize(1);
        assertThat(fila.get(0)).doesNotContain(temporal).doesNotContain(NUEVA);
        String hash = jdbc.queryForObject("SELECT password_hash FROM auth_schema.usuarios WHERE username = ?", String.class, CORREO);
        assertThat(hash).startsWith("$2").isNotEqualTo(NUEVA);
        List<String> bitacora = jdbc.queryForList("SELECT row(b.*)::text FROM auth_schema.bitacora_seguridad b WHERE b.username LIKE 'itest-hu37-%' OR b.detalle LIKE '%itest-hu37-%'", String.class);
        assertThat(bitacora).isNotEmpty().allSatisfy(f -> assertThat(f).doesNotContain(temporal).doesNotContain(NUEVA));
    }

    @Test
    void laBitacoraAnotaLaCreacionYElCambio() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();
        String restringido = cuerpo(entrar(CORREO, temporal, 200)).get("token").asText();
        cambiar(restringido, NUEVA, 200);

        List<Map<String, Object>> eventos = jdbc.queryForList(
                "SELECT evento, username, detalle FROM auth_schema.bitacora_seguridad WHERE username = ? OR detalle LIKE ? ORDER BY id",
                CORREO, "%" + CORREO + "%");
        assertThat(eventos).extracting(e -> e.get("evento")).contains("CUENTA_CLIENTE_CREADA", "LOGIN_EXITOSO", "CAMBIO_PASSWORD");
        Map<String, Object> creada = eventos.stream().filter(e -> "CUENTA_CLIENTE_CREADA".equals(e.get("evento"))).findFirst().orElseThrow();
        assertThat(creada.get("username")).isEqualTo(admin); // la crea el contador, no el cliente
        assertThat((String) creada.get("detalle")).contains("cliente " + CLIENTE);
    }

    // ---- una sola cuenta por empresa

    @Test
    void unaSegundaCuentaParaLaMismaEmpresaDa409YNoSeCrea() throws Exception {
        String token = contador();
        crear(token, CLIENTE, CORREO, 201);

        crear(token, CLIENTE, "itest-hu37-otro@acme.pe", 409);

        assertThat(filasDeLaEmpresa(CLIENTE)).isEqualTo(1);
    }

    @Test
    void unCorreoYaRegistradoDa409YNoCreaLaCuentaDeLaOtraEmpresa() throws Exception {
        String token = contador();
        crear(token, CLIENTE, CORREO, 201);

        crear(token, OTRO_CLIENTE, CORREO, 409);

        assertThat(filasDeLaEmpresa(OTRO_CLIENTE)).isZero();
    }

    @Test
    void elIndiceUnicoParcialImpideDosCuentasDeLaMismaEmpresaPeroNoLimitaALasDelEstudio() {
        String sql = "INSERT INTO auth_schema.usuarios (username, password_hash, nombre_completo, cliente_id) VALUES (?, 'x', 'prueba', ?)";
        jdbc.update(sql, "itest-hu37-i1", CLIENTE);

        assertThatThrownBy(() -> jdbc.update(sql, "itest-hu37-i2", CLIENTE)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_usuarios_cliente_id");
        jdbc.update(sql, "itest-hu37-i3", OTRO_CLIENTE);
        jdbc.update(sql, "itest-hu37-i4", null); // las cuentas del estudio no llevan empresa: varias sin cliente_id
        jdbc.update(sql, "itest-hu37-i5", null);
        assertThat(filasDeLaEmpresa(CLIENTE)).isEqualTo(1);
    }

    @Test
    void unClienteQueNoExisteEnMsTributarioDa404YNoSeCreaNada() throws Exception {
        when(tributario.buscar(eq(555L), any())).thenReturn(Optional.empty());

        crear(contador(), 555L, CORREO, 404);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_schema.usuarios WHERE username = ?", Integer.class, CORREO)).isZero();
    }

    @Test
    void unCorreoInvalidoDa400() throws Exception {
        crear(contador(), CLIENTE, "no-es-un-correo", 400);

        assertThat(filasDeLaEmpresa(CLIENTE)).isZero();
    }

    // ---- permisos

    @Test
    void sinSesionDa401YUnClienteDa403() throws Exception {
        crear(null, CLIENTE, CORREO, 401);
        crear("Bearer " + jwt.generate("alguien", List.of("CLIENTE_MYPE"), CLIENTE), CLIENTE, CORREO, 403);
        crear("Bearer " + jwt.generate("alguien", List.of()), CLIENTE, CORREO, 403);

        assertThat(filasDeLaEmpresa(CLIENTE)).isZero();
    }

    @Test
    void unContadorTambienPuedeCrearLaCuenta() throws Exception {
        crear("Bearer " + jwt.generate("itest-hu37-contador", List.of("CONTADOR")), CLIENTE, CORREO, 201);

        assertThat(filasDeLaEmpresa(CLIENTE)).isEqualTo(1);
    }

    @Test
    void conUnTokenNormalNoSePuedeUsarElCambioObligatorio() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();
        String restringido = cuerpo(entrar(CORREO, temporal, 200)).get("token").asText();
        String normal = cuerpo(cambiar(restringido, NUEVA, 200)).get("token").asText();

        cambiar(normal, "OtraClave456", 403);

        assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM auth_schema.usuarios WHERE username = ?", Boolean.class, CORREO)).isFalse();
    }

    @Test
    void laNuevaContraseniaInvalidaNoCambiaNadaYSePuedeReintentarConElMismoToken() throws Exception {
        String temporal = cuerpo(crear(contador(), CLIENTE, CORREO, 201)).get("passwordTemporal").asText();
        String restringido = cuerpo(entrar(CORREO, temporal, 200)).get("token").asText();

        cambiar(restringido, "corta", 400);
        cambiar(restringido, temporal, 400);
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM auth_schema.usuarios WHERE username = ?", Boolean.class, CORREO)).isTrue();
        cambiar(restringido, NUEVA, 200);
    }
}
