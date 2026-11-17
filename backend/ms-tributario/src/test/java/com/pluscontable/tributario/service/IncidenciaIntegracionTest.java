package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-23 · Tarea 164: las incidencias de punta a punta (registrar, verlas en el historial del cliente, la general que se
 * asocia a la cartera activa) y sus permisos por rol. Prueba de integración con PostgreSQL y HTTP reales; usa clientes
 * ficticios que se borran al terminar. Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class IncidenciaIntegracionTest {

    private static final String BASE = "/api/tributario";
    private static final LocalDate HOY = LocalDate.now();

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();
    private final List<Long> incidenciasCreadas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : incidenciasCreadas) {
            jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id = ?", id);
        }
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpHeaders cabeceras(String usuario, String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject(usuario).claim("roles", List.of(rol))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000));
        if (clienteId != null) token.claim("clienteId", clienteId);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token.signWith(key).compact());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders contador() {
        return cabeceras("contador1", "CONTADOR", null);
    }

    private HttpHeaders sinSesion() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (incidencias e2e)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE + "/clientes", HttpMethod.POST, new HttpEntity<>(dto, contador()), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private static Map<String, Object> cuerpo(String tipo, Long cliente, String descripcion) {
        var m = new HashMap<String, Object>();
        m.put("tipo", tipo);
        m.put("clienteId", cliente);
        m.put("fecha", HOY.toString());
        m.put("descripcion", descripcion);
        return m;
    }

    private ResponseEntity<String> registrarTexto(Object cuerpo, HttpHeaders cab) {
        return http.exchange(BASE + "/incidencias", HttpMethod.POST, new HttpEntity<>(cuerpo, cab), String.class);
    }

    private IncidenciaDto registrar(Object cuerpo, HttpHeaders cab) {
        ResponseEntity<IncidenciaDto> r = http.exchange(BASE + "/incidencias", HttpMethod.POST, new HttpEntity<>(cuerpo, cab), IncidenciaDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        incidenciasCreadas.add(r.getBody().id());
        return r.getBody();
    }

    private ResponseEntity<String> historialTexto(long cliente, HttpHeaders cab) {
        return http.exchange(BASE + "/clientes/" + cliente + "/incidencias", HttpMethod.GET, new HttpEntity<>(cab), String.class);
    }

    private List<IncidenciaDto> historial(long cliente) {
        ResponseEntity<List<IncidenciaDto>> r = http.exchange(BASE + "/clientes/" + cliente + "/incidencias", HttpMethod.GET,
                new HttpEntity<>(contador()), new ParameterizedTypeReference<>() { });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private int contarIncidencias() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.incidencias", Integer.class);
    }

    @Test
    void criterios1y2LaIncidenciaDelClienteQuedaEnLaBaseYEnSuHistorial() {
        long cliente = crearCliente();
        long otro = crearCliente();

        IncidenciaDto i = registrar(cuerpo("CLIENTE_NO_ENVIA_DOCUMENTOS", cliente, "  No envió las facturas de octubre  "), contador());

        assertThat(i.clientesAsociados()).isEqualTo(1);
        assertThat(i.descripcion()).isEqualTo("No envió las facturas de octubre");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.incidencias_clientes WHERE incidencia_id = ? AND cliente_id = ?",
                Integer.class, i.id(), cliente)).isEqualTo(1);
        assertThat(historial(cliente)).extracting(IncidenciaDto::id).containsExactly(i.id());
        assertThat(historial(otro)).isEmpty();
    }

    @Test
    void cadaTipoDeLaListaDeBetoSeRegistraYApareceEnElHistorialDelCliente() {
        long cliente = crearCliente();
        List<String> nuevos = List.of("PROBLEMA_SIRE", "PROBLEMA_SERVICIOS_EXTERNOS", "FALLA_SISTEMA_INTERNO",
                "PROBLEMA_CONEXION_INTERNET", "ERROR_PROCESO_CONTABLE");

        for (String tipo : nuevos) {
            IncidenciaDto i = registrar(cuerpo(tipo, cliente, "Descripción de " + tipo), contador());
            assertThat(i.tipo().name()).isEqualTo(tipo);
            assertThat(i.clientesAsociados()).isEqualTo(1);
        }

        assertThat(historial(cliente)).extracting(i -> i.tipo().name()).containsExactlyInAnyOrderElementsOf(nuevos);
        assertThat(jdbc.queryForList("SELECT tipo FROM tributario_schema.incidencias WHERE id IN (SELECT incidencia_id FROM tributario_schema.incidencias_clientes WHERE cliente_id = ?)",
                String.class, cliente)).containsExactlyInAnyOrderElementsOf(nuevos);
    }

    @Test
    void losTiposNuevosNoSonGeneralesYSinClienteSeRechazan() {
        for (String tipo : List.of("PROBLEMA_SIRE", "PROBLEMA_SERVICIOS_EXTERNOS", "FALLA_SISTEMA_INTERNO", "PROBLEMA_CONEXION_INTERNET", "ERROR_PROCESO_CONTABLE")) {
            int antes = contarIncidencias();
            ResponseEntity<String> r = registrarTexto(cuerpo(tipo, null, "x"), contador());
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody()).contains("clienteId");
            assertThat(contarIncidencias()).isEqualTo(antes);
        }
    }

    @Test
    void lasObservacionesSeGuardanSeDevuelvenYSonOpcionales() {
        long cliente = crearCliente();
        Map<String, Object> conNotas = cuerpo("OTRO", cliente, "Descripción");
        conNotas.put("observaciones", "  Se llamó al cliente  ");

        IncidenciaDto con = registrar(conNotas, contador());
        IncidenciaDto sin = registrar(cuerpo("OTRO", cliente, "Otra"), contador());

        assertThat(con.observaciones()).isEqualTo("Se llamó al cliente");
        assertThat(sin.observaciones()).isNull();
        assertThat(jdbc.queryForObject("SELECT observaciones FROM tributario_schema.incidencias WHERE id = ?", String.class, con.id()))
                .isEqualTo("Se llamó al cliente");
        assertThat(historial(cliente)).filteredOn(i -> i.id().equals(con.id())).singleElement()
                .satisfies(i -> assertThat(i.observaciones()).isEqualTo("Se llamó al cliente"));
        assertThat(historial(cliente)).filteredOn(i -> i.id().equals(sin.id())).singleElement()
                .satisfies(i -> assertThat(i.observaciones()).isNull());
    }

    @Test
    void lasObservacionesDeMasDeMilCaracteresDan400YNoSeGuarda() {
        long cliente = crearCliente();
        Map<String, Object> larga = cuerpo("OTRO", cliente, "Descripción");
        larga.put("observaciones", "o".repeat(1001));
        int antes = contarIncidencias();

        ResponseEntity<String> r = registrarTexto(larga, contador());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("observaciones (máximo 1000 caracteres)");
        assertThat(contarIncidencias()).isEqualTo(antes);
    }

    @Test
    void criterio3LaCaidaDelPortalSolSeAsociaASoloLosClientesActivosYSeVeEnCadaHistorial() {
        long activo = crearCliente();
        long inactivo = crearCliente();
        jdbc.update("UPDATE tributario_schema.clientes SET activo = false WHERE id = ?", inactivo);
        int activos = jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE activo", Integer.class);

        IncidenciaDto general = registrar(cuerpo("CAIDA_PORTAL_SOL", null, "El portal SOL no cargó"), contador());

        assertThat(general.clientesAsociados()).isEqualTo(activos);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.incidencias_clientes WHERE incidencia_id = ?",
                Integer.class, general.id())).isEqualTo(activos);
        assertThat(historial(activo)).extracting(IncidenciaDto::id).contains(general.id());
        assertThat(historial(inactivo)).isEmpty();
    }

    @Test
    void elHistorialMuestraLaMasRecientePrimeroConLasPropiasYLasGenerales() {
        long cliente = crearCliente();
        var vieja = cuerpo("OTRO", cliente, "Antigua");
        vieja.put("fecha", HOY.minusDays(5).toString());
        IncidenciaDto antigua = registrar(vieja, contador());
        IncidenciaDto general = registrar(cuerpo("CAIDA_PORTAL_SOL", null, "Hoy cayó el portal"), contador());

        assertThat(historial(cliente)).extracting(IncidenciaDto::id).containsExactly(general.id(), antigua.id());
    }

    @Test
    void criterio4ElUsuarioQueRegistraSaleDelTokenYTambienRegistraElAdministrador() {
        long cliente = crearCliente();
        var conUsuarioFalso = cuerpo("OTRO", cliente, "Con usuario en el cuerpo");
        conUsuarioFalso.put("registradaPor", "alguien-mas");

        IncidenciaDto delContador = registrar(conUsuarioFalso, cabeceras("contador1", "CONTADOR", null));
        IncidenciaDto delAdmin = registrar(cuerpo("OTRO", cliente, "Del admin"), cabeceras("admin", "ADMIN", null));

        assertThat(delContador.registradaPor()).isEqualTo("contador1");
        assertThat(delAdmin.registradaPor()).isEqualTo("admin");
        assertThat(jdbc.queryForObject("SELECT registrada_por FROM tributario_schema.incidencias WHERE id = ?", String.class, delContador.id()))
                .isEqualTo("contador1");
    }

    @Test
    void criterio4SinSesionDa401YUnClienteMypeDa403SinGuardarNada() {
        long cliente = crearCliente();
        int antes = contarIncidencias();
        HttpHeaders mype = cabeceras("cliente1", "CLIENTE_MYPE", cliente);

        assertThat(registrarTexto(cuerpo("OTRO", cliente, "x"), sinSesion()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(registrarTexto(cuerpo("OTRO", cliente, "x"), mype).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(historialTexto(cliente, sinSesion()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(historialTexto(cliente, mype).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(contarIncidencias()).isEqualTo(antes);
    }

    @Test
    void losDatosInvalidosDan400ConTodosLosErroresYElClienteInexistenteDa404SinGuardar() {
        long cliente = crearCliente();
        int antes = contarIncidencias();

        var invalida = cuerpo("OTRO", null, "   ");
        invalida.put("fecha", HOY.plusDays(1).toString());
        ResponseEntity<String> r400 = registrarTexto(invalida, contador());
        assertThat(r400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r400.getBody()).contains("clienteId", "fecha (no puede ser futura)", "descripcion");

        assertThat(registrarTexto(cuerpo("INVENTADO", cliente, "x"), contador()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registrarTexto(cuerpo("CAIDA_PORTAL_SOL", cliente, "general con cliente"), contador()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registrarTexto(cuerpo("OTRO", 999_999_999L, "x"), contador()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(historialTexto(999_999_999L, contador()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(contarIncidencias()).isEqualTo(antes);
    }
}
