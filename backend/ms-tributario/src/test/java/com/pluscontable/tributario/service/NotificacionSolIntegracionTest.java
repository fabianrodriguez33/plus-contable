package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.NotificacionSolDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-18 · Tarea 71: notificaciones del Buzón SOL de punta a punta (registrar, ver pendientes en la base, marcar como
 * atendida) y sus permisos. Prueba de integración con PostgreSQL y HTTP reales; usa clientes ficticios que se borran
 * al terminar (las notificaciones se borran en cascada). Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class NotificacionSolIntegracionTest {

    private static final String BASE = "/api/tributario";
    private static final LocalDate HOY = LocalDate.now();

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();

    @BeforeEach
    void permitirPatch() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpHeaders cabeceras(List<String> roles, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject("usuario-prueba").claim("roles", roles)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000));
        if (clienteId != null) token.claim("clienteId", clienteId);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token.signWith(key).compact());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders contador() {
        return cabeceras(List.of("CONTADOR"), null);
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (notificaciones)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE + "/clientes", HttpMethod.POST, new HttpEntity<>(dto, contador()), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private static Map<String, Object> cuerpo(String tipo, LocalDate recepcion, LocalDate plazo) {
        var m = new java.util.HashMap<String, Object>();
        m.put("tipo", tipo);
        m.put("fechaRecepcion", recepcion == null ? null : recepcion.toString());
        m.put("plazoRespuesta", plazo == null ? null : plazo.toString());
        return m;
    }

    private ResponseEntity<String> registrarTexto(long cliente, Object cuerpo, HttpHeaders cab) {
        return http.exchange(BASE + "/clientes/" + cliente + "/notificaciones", HttpMethod.POST, new HttpEntity<>(cuerpo, cab), String.class);
    }

    private NotificacionSolDto registrar(long cliente, String tipo, LocalDate plazo) {
        ResponseEntity<NotificacionSolDto> r = http.exchange(BASE + "/clientes/" + cliente + "/notificaciones", HttpMethod.POST,
                new HttpEntity<>(cuerpo(tipo, HOY.minusDays(2), plazo), contador()), NotificacionSolDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private ResponseEntity<String> atender(long id, HttpHeaders cab) {
        return http.exchange(BASE + "/notificaciones/" + id + "/atencion", HttpMethod.PATCH, new HttpEntity<>(cab), String.class);
    }

    private List<Long> pendientesDelCliente(long cliente) {
        return jdbc.queryForList("SELECT id FROM tributario_schema.notificaciones_sol WHERE cliente_id = ? AND estado = 'PENDIENTE' ORDER BY plazo_respuesta",
                Long.class, cliente);
    }

    private int filas(long cliente) {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.notificaciones_sol WHERE cliente_id = ?", Integer.class, cliente);
    }

    @Test
    void registrarGuardaLaNotificacionPendienteYQuedaEnLosPendientesDelCliente() {
        long cliente = crearCliente();

        NotificacionSolDto n = registrar(cliente, "CARTA_INDUCTIVA", HOY.plusDays(15));

        assertThat(n.id()).isNotNull();
        assertThat(n.estado().name()).isEqualTo("PENDIENTE");
        assertThat(n.fechaAtencion()).isNull();
        Map<String, Object> fila = jdbc.queryForMap("SELECT tipo, fecha_recepcion, plazo_respuesta, estado FROM tributario_schema.notificaciones_sol WHERE id = ?", n.id());
        assertThat(fila.get("tipo")).isEqualTo("CARTA_INDUCTIVA");
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(((java.sql.Date) fila.get("plazo_respuesta")).toLocalDate()).isEqualTo(HOY.plusDays(15));
        assertThat(pendientesDelCliente(cliente)).containsExactly(n.id());
    }

    @Test
    void sinPlazoSinTipoOConFechasIncoherentesResponde400YNoGuardaNada() {
        long cliente = crearCliente();

        ResponseEntity<String> sinPlazo = registrarTexto(cliente, cuerpo("ESQUELA", HOY, null), contador());
        ResponseEntity<String> sinNada = registrarTexto(cliente, cuerpo(null, null, null), contador());
        ResponseEntity<String> plazoAnterior = registrarTexto(cliente, cuerpo("ESQUELA", HOY, HOY.minusDays(1)), contador());
        ResponseEntity<String> recepcionFutura = registrarTexto(cliente, cuerpo("ESQUELA", HOY.plusDays(1), HOY.plusDays(10)), contador());
        ResponseEntity<String> tipoInvalido = registrarTexto(cliente, cuerpo("OTRO", HOY, HOY.plusDays(5)), contador());

        assertThat(sinPlazo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sinPlazo.getBody()).contains("Faltan o son inválidos").contains("plazoRespuesta");
        assertThat(sinNada.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sinNada.getBody()).contains("tipo").contains("fechaRecepcion").contains("plazoRespuesta");
        assertThat(plazoAnterior.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(recepcionFutura.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tipoInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(filas(cliente)).isZero();
    }

    @Test
    void conUnClienteInexistenteResponde404() {
        ResponseEntity<String> r = registrarTexto(Long.MAX_VALUE, cuerpo("ESQUELA", HOY, HOY.plusDays(5)), contador());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void marcarAtendidaGuardaLaFechaDeHoyYLaSacaDeLosPendientes() {
        long cliente = crearCliente();
        NotificacionSolDto primera = registrar(cliente, "ESQUELA", HOY.plusDays(20));
        NotificacionSolDto segunda = registrar(cliente, "COMUNICADO", HOY.plusDays(5));
        assertThat(pendientesDelCliente(cliente)).containsExactly(segunda.id(), primera.id()); // la más urgente primero

        ResponseEntity<NotificacionSolDto> r = http.exchange(BASE + "/notificaciones/" + segunda.id() + "/atencion", HttpMethod.PATCH,
                new HttpEntity<>(contador()), NotificacionSolDto.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().estado().name()).isEqualTo("ATENDIDA");
        assertThat(r.getBody().fechaAtencion()).isEqualTo(HOY);
        Map<String, Object> fila = jdbc.queryForMap("SELECT estado, fecha_atencion FROM tributario_schema.notificaciones_sol WHERE id = ?", segunda.id());
        assertThat(fila.get("estado")).isEqualTo("ATENDIDA");
        assertThat(((java.sql.Date) fila.get("fecha_atencion")).toLocalDate()).isEqualTo(HOY);
        assertThat(pendientesDelCliente(cliente)).containsExactly(primera.id());
    }

    @Test
    void atenderDosVecesResponde400YConservaLaFechaOriginal() {
        long cliente = crearCliente();
        NotificacionSolDto n = registrar(cliente, "ESQUELA", HOY.plusDays(7));
        atender(n.id(), contador());
        jdbc.update("UPDATE tributario_schema.notificaciones_sol SET fecha_atencion = ? WHERE id = ?", HOY.minusDays(1), n.id());

        ResponseEntity<String> segunda = atender(n.id(), contador());

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(segunda.getBody()).contains("La notificación ya fue atendida");
        assertThat(jdbc.queryForObject("SELECT fecha_atencion FROM tributario_schema.notificaciones_sol WHERE id = ?", java.sql.Date.class, n.id()).toLocalDate())
                .isEqualTo(HOY.minusDays(1));
    }

    @Test
    void atenderUnaNotificacionInexistenteResponde404() {
        assertThat(atender(Long.MAX_VALUE, contador()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sinSesionResponde401EnLasDosRutasYNoSeGuardaNiSeCambiaNada() {
        long cliente = crearCliente();
        NotificacionSolDto existente = registrar(cliente, "ESQUELA", HOY.plusDays(7));

        ResponseEntity<String> registrar = http.exchange(BASE + "/clientes/" + cliente + "/notificaciones", HttpMethod.POST,
                new HttpEntity<>(cuerpo("ESQUELA", HOY, HOY.plusDays(3))), String.class);
        ResponseEntity<String> atender = http.exchange(BASE + "/notificaciones/" + existente.id() + "/atencion", HttpMethod.PATCH,
                HttpEntity.EMPTY, String.class);

        assertThat(registrar.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(atender.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(filas(cliente)).isEqualTo(1);
        assertThat(pendientesDelCliente(cliente)).containsExactly(existente.id());
    }

    @Test
    void unClienteMypeNoRegistraNiAtiendeNiSiquieraLasDeSuPropiaEmpresa() {
        long cliente = crearCliente();
        NotificacionSolDto existente = registrar(cliente, "ESQUELA", HOY.plusDays(7));
        HttpHeaders mype = cabeceras(List.of("CLIENTE_MYPE"), cliente);

        ResponseEntity<String> registrar = registrarTexto(cliente, cuerpo("ESQUELA", HOY, HOY.plusDays(3)), mype);
        ResponseEntity<String> atender = atender(existente.id(), mype);

        assertThat(registrar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(atender.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(filas(cliente)).isEqualTo(1);
        assertThat(pendientesDelCliente(cliente)).containsExactly(existente.id());
    }

    @Test
    void elAdministradorDelEstudioTambienRegistraYAtiende() {
        long cliente = crearCliente();
        HttpHeaders admin = cabeceras(List.of("ADMIN"), null);

        ResponseEntity<NotificacionSolDto> creada = http.exchange(BASE + "/clientes/" + cliente + "/notificaciones", HttpMethod.POST,
                new HttpEntity<>(cuerpo("COMUNICADO", HOY, HOY.plusDays(9)), admin), NotificacionSolDto.class);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(atender(creada.getBody().id(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendientesDelCliente(cliente)).isEmpty();
    }
}
