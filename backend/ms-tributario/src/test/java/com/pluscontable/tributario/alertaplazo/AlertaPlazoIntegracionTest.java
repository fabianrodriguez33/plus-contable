package com.pluscontable.tributario.alertaplazo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-19 (tarea 94): las alertas del plazo de respuesta de punta a punta con HTTP y PostgreSQL reales. Las notificaciones se
 * registran y se atienden por los endpoints de HU-18; los avisos salen del núcleo de HU-12. Clientes ficticios que se borran
 * al terminar (primero sus alertas y avisos: un cliente con avisos no se puede borrar). Las fechas son relativas al «hoy» de Lima.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AlertaPlazoIntegracionTest {

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;
    @Autowired AlertaPlazoService service;
    @Autowired AlertaPlazoRepositorio repositorio;
    @Autowired AvisoInternoService avisos;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final LocalDate hoy = LocalDate.now(AlertaPlazoService.ZONA);
    private final List<Long> clientes = new ArrayList<>();

    private long clienteA;
    private long clienteB;
    private long cartaA;        // A, carta inductiva, plazo en 5 días
    private long esquelaA;      // A, esquela, plazo en 4 días (hoy no toca)
    private long cartaB;        // B, carta inductiva, plazo en 3 días

    @BeforeEach
    void crear() throws Exception {
        clienteA = cliente("Aaa cliente ficticio (alertas)");
        clienteB = cliente("Bbb cliente ficticio (alertas)");
        cartaA = notificacion(clienteA, "CARTA_INDUCTIVA", hoy.plusDays(5));
        esquelaA = notificacion(clienteA, "ESQUELA", hoy.plusDays(4));
        cartaB = notificacion(clienteB, "CARTA_INDUCTIVA", hoy.plusDays(3));
    }

    @AfterEach
    void borrar() {
        for (Long c : clientes) {
            List<Long> avisosDelCliente = jdbc.queryForList("SELECT a.aviso_id FROM tributario_schema.alertas_plazo_notificacion a "
                    + "JOIN tributario_schema.notificaciones_sol n ON n.id = a.notificacion_id WHERE n.cliente_id = ?", Long.class, c);
            jdbc.update("DELETE FROM tributario_schema.alertas_plazo_notificacion WHERE notificacion_id IN "
                    + "(SELECT id FROM tributario_schema.notificaciones_sol WHERE cliente_id = ?)", c);
            for (Long aviso : avisosDelCliente) jdbc.update("DELETE FROM tributario_schema.avisos WHERE id = ?", aviso); // sus clientes caen en cascada
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", c);
        }
    }

    private long cliente(String razon) {
        String ruc = "28" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, ?, 'RER')", ruc, razon);
        long id = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        clientes.add(id);
        return id;
    }

    private String token(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("contador1").claim("roles", List.of(roles)).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String... roles) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Content-Type", "application/json");
        if (roles.length > 0) b.header("Authorization", "Bearer " + token(roles));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Registra la notificación por el endpoint de HU-18. */
    private long notificacion(long cliente, String tipo, LocalDate plazo) throws Exception {
        String cuerpo = "{\"tipo\":\"" + tipo + "\",\"fechaRecepcion\":\"" + hoy + "\",\"plazoRespuesta\":\"" + plazo + "\"}";
        HttpResponse<String> r = enviar("POST", "/api/tributario/clientes/" + cliente + "/notificaciones", cuerpo, "CONTADOR");
        assertThat(r.statusCode()).isEqualTo(201);
        return json.readTree(r.body()).get("id").asLong();
    }

    private JsonNode alertas() throws Exception {
        HttpResponse<String> r = enviar("GET", "/api/tributario/alertas-plazo", null, "CONTADOR");
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body());
    }

    private List<Long> notificacionesConAlerta(JsonNode alertas) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode a : alertas) if (clientes.contains(a.get("clienteId").asLong())) ids.add(a.get("notificacionId").asLong());
        return ids;
    }

    private int cuenta(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int alertasDe(long notificacion) {
        return cuenta("SELECT count(*) FROM tributario_schema.alertas_plazo_notificacion WHERE notificacion_id = ?", notificacion);
    }

    // ---- criterio 1: el día de aviso llega la alerta

    @Test
    void criterio1_elDiaDeAvisoLlegaLaAlertaDeLasNotificacionesPendientesYSoloDeEsas() throws Exception {
        service.generar(hoy);

        assertThat(notificacionesConAlerta(alertas())).containsExactlyInAnyOrder(cartaA, cartaB); // faltan 5 y 3; la esquela (4) no
        assertThat(alertasDe(esquelaA)).isZero();
    }

    // ---- criterio 2: escalonadas

    @Test
    void criterio2_haceMasDeUnaAlertaAntesDelPlazoCadaUnaUnaSolaVez() throws Exception {
        for (int dia : new int[] {0, 2, 4, 5}) service.generar(hoy.plusDays(dia)); // faltan 5, 3, 1 y 0 para la carta de A
        int avisosAntes = cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE origen = 'HU-19'");

        List<Integer> escalones = new ArrayList<>();
        for (JsonNode a : alertas()) if (a.get("notificacionId").asLong() == cartaA) escalones.add(a.get("diasAntes").asInt());
        assertThat(escalones).containsExactlyInAnyOrder(5, 3, 1, 0);

        // repetir cualquiera de esos días no duplica ni alertas ni avisos
        assertThat(service.generar(hoy.plusDays(2))).isZero();
        assertThat(alertasDe(cartaA)).isEqualTo(4);
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE origen = 'HU-19'")).isEqualTo(avisosAntes);
    }

    @Test
    void criterio2_repetirElProcesoElMismoDiaNoDuplicaNada() {
        assertThat(service.generar(hoy)).isGreaterThanOrEqualTo(2);

        assertThat(service.generar(hoy)).isZero();
        assertThat(alertasDe(cartaA)).isEqualTo(1);
        assertThat(alertasDe(cartaB)).isEqualTo(1);
    }

    // ---- criterio 3: la alerta muestra el cliente y los días restantes

    @Test
    void criterio3_laAlertaTraeElClienteElTipoElPlazoYLosDiasRestantesDeHoy() throws Exception {
        service.generar(hoy);

        JsonNode a = null;
        for (JsonNode x : alertas()) if (x.get("notificacionId").asLong() == cartaB) a = x;
        assertThat(a).isNotNull();
        assertThat(a.get("razonSocial").asText()).startsWith("Bbb");
        assertThat(a.get("ruc").asText()).hasSize(11);
        assertThat(a.get("clienteId").asLong()).isEqualTo(clienteB);
        assertThat(a.get("tipo").asText()).isEqualTo("CARTA_INDUCTIVA");
        assertThat(a.get("plazoRespuesta").asText()).isEqualTo(hoy.plusDays(3).toString());
        assertThat(a.get("diasRestantes").asLong()).isEqualTo(3);
        assertThat(a.get("diasAntes").asInt()).isEqualTo(3);
        // el aviso compartido de HU-12 queda con el cliente y el mensaje
        String mensaje = jdbc.queryForObject("SELECT mensaje FROM tributario_schema.avisos WHERE clave_evento = ?", String.class,
                "notificacion:" + cartaB + ":dias:3");
        assertThat(mensaje).contains("Bbb cliente ficticio").contains("faltan 3 días").contains("carta inductiva");
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos_clientes ac JOIN tributario_schema.avisos a ON a.id = ac.aviso_id "
                + "WHERE a.clave_evento = ? AND ac.cliente_id = ?", "notificacion:" + cartaB + ":dias:3", clienteB)).isEqualTo(1);
    }

    @Test
    void losDiasRestantesSeCalculanAlConsultarYLasAlertasVanPorPlazoMasProximo() throws Exception {
        service.generar(hoy);
        service.generar(hoy.plusDays(2)); // la carta de B: faltan 1

        List<Long> orden = new ArrayList<>();
        for (JsonNode a : alertas()) if (clientes.contains(a.get("clienteId").asLong())) orden.add(a.get("notificacionId").asLong());

        assertThat(orden.get(0)).isEqualTo(cartaB);   // plazo en 3 días
        assertThat(orden).endsWith(cartaA);           // plazo en 5 días, al final
    }

    // ---- criterio 4: atendida, no hay alerta

    @Test
    void criterio4_unaNotificacionAtendidaNoGeneraAlertasNuevasYSusAlertasDejanDeListarse() throws Exception {
        service.generar(hoy);
        assertThat(notificacionesConAlerta(alertas())).contains(cartaB);

        assertThat(enviar("PATCH", "/api/tributario/notificaciones/" + cartaB + "/atencion", null, "CONTADOR").statusCode()).isEqualTo(200);

        assertThat(notificacionesConAlerta(alertas())).containsOnly(cartaA); // solo la carta de A (puede tener varias alertas)
        service.generar(hoy.plusDays(2)); // cuando a la carta de B le tocaría el escalón de 1 día
        assertThat(alertasDe(cartaB)).isEqualTo(1); // sigue con la de antes: ninguna nueva
        assertThat(notificacionesConAlerta(alertas())).containsOnly(cartaA); // solo la carta de A (puede tener varias alertas)
    }

    @Test
    void criterio4_unaNotificacionAtendidaAntesDeSuDiaDeAvisoNuncaRecibeAlerta() throws Exception {
        long otra = notificacion(clienteA, "COMUNICADO", hoy.plusDays(1));
        assertThat(enviar("PATCH", "/api/tributario/notificaciones/" + otra + "/atencion", null, "CONTADOR").statusCode()).isEqualTo(200);

        service.generar(hoy);

        assertThat(alertasDe(otra)).isZero();
    }

    @Test
    void unPlazoQueYaPasoNoSeListaComoAlerta() throws Exception {
        service.generar(hoy);
        jdbc.update("UPDATE tributario_schema.notificaciones_sol SET plazo_respuesta = ?, fecha_recepcion = ? WHERE id = ?",
                hoy.minusDays(1), hoy.minusDays(10), cartaB);

        assertThat(notificacionesConAlerta(alertas())).doesNotContain(cartaB);
    }

    // ---- «hoy» de Lima

    @Test
    void hoyEsElDeLimaAunqueElServidorEsteEnUtc() throws Exception {
        // 21:00 en Lima de hoy = 02:00 UTC de mañana: en UTC ya sería «mañana» y la carta de B (3 días) estaría a 2
        AlertaPlazoService enLaNoche = new AlertaPlazoService(repositorio, avisos, List.of(5, 3, 1, 0),
                Clock.fixed(Instant.parse(hoy.plusDays(1) + "T02:00:00Z"), AlertaPlazoService.ZONA));

        enLaNoche.generarDeHoy();

        assertThat(alertasDe(cartaB)).isEqualTo(1);
        assertThat(alertasDe(cartaA)).isEqualTo(1);
        assertThat(alertasDe(esquelaA)).isZero();
    }

    // ---- permisos

    @Test
    void permisos_sinSesion401UnClienteMypeOUnTokenSinRol403ElContadorYElAdministradorVenLasAlertas() throws Exception {
        service.generar(hoy);

        assertThat(enviar("GET", "/api/tributario/alertas-plazo", null).statusCode()).isEqualTo(401);
        HttpResponse<String> mype = enviar("GET", "/api/tributario/alertas-plazo", null, "CLIENTE_MYPE");
        assertThat(mype.statusCode()).isEqualTo(403);
        assertThat(mype.body()).doesNotContain("razonSocial");
        assertThat(enviar("GET", "/api/tributario/alertas-plazo", null, "OTRO_ROL").statusCode()).isEqualTo(403);
        assertThat(enviar("GET", "/api/tributario/alertas-plazo", null, "CONTADOR").statusCode()).isEqualTo(200);
        assertThat(enviar("GET", "/api/tributario/alertas-plazo", null, "ADMIN").statusCode()).isEqualTo(200);
    }

    @Test
    void elTableDeAlertasNoGuardaEstadosNiDiasRestantes() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema' AND table_name = 'alertas_plazo_notificacion' ORDER BY ordinal_position
                """, String.class);

        assertThat(columnas).containsExactly("id", "notificacion_id", "dias_antes", "fecha_alerta", "aviso_id", "creada_en");
    }

    @Test
    void alAtenderUnaNotificacionSuAvisoSaleDeLaBandejaNoLeidaYElDeLasDemasSeQueda() throws Exception {
        service.generar(hoy);
        String claveB = "notificacion:" + cartaB + ":dias:3";
        String claveA = "notificacion:" + cartaA + ":dias:5";
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento = ? AND leido_en IS NULL", claveB)).isEqualTo(1);

        assertThat(enviar("PATCH", "/api/tributario/notificaciones/" + cartaB + "/atencion", null, "CONTADOR").statusCode()).isEqualTo(200);

        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento = ? AND leido_en IS NOT NULL", claveB)).isEqualTo(1);
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento = ? AND leido_en IS NULL", claveA)).isEqualTo(1);
        // lo que ve el contador en la bandeja del núcleo: sin leer solo queda el de la otra notificación
        HttpResponse<String> bandeja = enviar("GET", "/api/tributario/avisos?leido=false", null, "CONTADOR");
        assertThat(bandeja.body()).contains(claveA).doesNotContain(claveB);
    }

    @Test
    void atenderUnaNotificacionConVariasAlertasMarcaTodosSusAvisos() throws Exception {
        service.generar(hoy);                // faltan 5
        service.generar(hoy.plusDays(2));    // faltan 3
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento LIKE ? AND leido_en IS NULL",
                "notificacion:" + cartaA + ":dias:%")).isEqualTo(2);

        assertThat(enviar("PATCH", "/api/tributario/notificaciones/" + cartaA + "/atencion", null, "CONTADOR").statusCode()).isEqualTo(200);

        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento LIKE ? AND leido_en IS NULL",
                "notificacion:" + cartaA + ":dias:%")).isZero();
    }

    @Test
    void atenderUnaNotificacionYaAtendidaDa400YNoTocaNadaMas() throws Exception {
        service.generar(hoy);
        enviar("PATCH", "/api/tributario/notificaciones/" + cartaB + "/atencion", null, "CONTADOR");

        assertThat(enviar("PATCH", "/api/tributario/notificaciones/" + cartaB + "/atencion", null, "CONTADOR").statusCode()).isEqualTo(400);
        assertThat(cuenta("SELECT count(*) FROM tributario_schema.avisos WHERE clave_evento = ? AND leido_en IS NULL",
                "notificacion:" + cartaA + ":dias:5")).isEqualTo(1);
    }
}
