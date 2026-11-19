package com.pluscontable.tributario.recordatoriopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-15 · T41, de punta a punta por HTTP real con PostgreSQL desechable: el contador programa un recordatorio, llega el día (el job
 * se invoca con la fecha de hoy en Lima) y el cliente lo ve como aviso de pago en su portal; nadie más lo ve ni lo programa.
 * Clientes y obligaciones ficticios que se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RecordatorioPagoHttpTest {

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final AtomicLong SECUENCIA = new AtomicLong(System.nanoTime() % 100_000_000L);
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordatorioPagoService recordatorios;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> clientes = new ArrayList<>();
    private int mes = 0;

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private static String rucFicticio() {
        String cuerpo = "20" + String.format("%08d", SECUENCIA.incrementAndGet() % 100_000_000L);
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) suma += (cuerpo.charAt(i) - '0') * FACTORES[i];
        int dv = 11 - (suma % 11);
        return cuerpo + (dv == 10 ? 0 : dv == 11 ? 1 : dv);
    }

    private long cliente() {
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario)
                VALUES (?, 'Cliente HU15 HTTP SAC', 'MYPE') RETURNING id""", Long.class, rucFicticio());
        clientes.add(id);
        return id;
    }

    private long declarada(long cliente) {
        LocalDate periodo = LocalDate.of(2020, 1, 1).plusMonths(mes++);
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado,
                                                           fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, 'IGV', ?, ?, 'DECLARADO', '2026-11-05', 'F-1', 1200.00) RETURNING id""", Long.class, cliente, periodo, HOY.plusDays(10));
    }

    private String token(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("contador1").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> llamar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String fecha(LocalDate f) {
        return "{\"fechaRecordatorio\":\"" + f + "\"}";
    }

    @Test
    void elContadorProgramaLlegaElDiaYElClienteLoRecibeEnSuPortal() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        long o = declarada(suyo);
        String ruta = "/api/tributario/obligaciones/" + o + "/recordatorios-pago";

        var creado = llamar("POST", ruta, token("CONTADOR", null), fecha(HOY));
        assertThat(creado.statusCode()).isEqualTo(201);
        JsonNode r = json.readTree(creado.body());
        assertThat(r.get("estado").asText()).isEqualTo("PROGRAMADO");
        assertThat(r.get("programadoPor").asText()).isEqualTo("contador1");
        // antes de que llegue el día, el cliente no tiene ningún aviso
        String avisos = "/api/tributario/clientes/" + suyo + "/avisos-pago";
        assertThat(json.readTree(llamar("GET", avisos, token("CLIENTE_MYPE", suyo), null).body())).isEmpty();

        assertThat(recordatorios.entregar(HOY).enviados()).isGreaterThanOrEqualTo(1);

        var visto = llamar("GET", avisos, token("CLIENTE_MYPE", suyo), null);
        assertThat(visto.statusCode()).isEqualTo(200);
        JsonNode lista = json.readTree(visto.body());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("obligacionId").asLong()).isEqualTo(o);
        assertThat(lista.get(0).get("tributo").asText()).isEqualTo("IGV");
        assertThat(lista.get(0).get("monto").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(lista.get(0).get("fechaVencimiento").asText()).isEqualTo(HOY.plusDays(10).toString());
        assertThat(lista.get(0).get("fechaAviso").asText()).isEqualTo(HOY.toString());
        // el estudio ve el recordatorio como enviado; otra empresa no ve el aviso
        assertThat(json.readTree(llamar("GET", ruta, token("CONTADOR", null), null).body()).get(0).get("estado").asText()).isEqualTo("ENVIADO");
        assertThat(llamar("GET", "/api/tributario/clientes/" + suyo + "/avisos-pago", token("CLIENTE_MYPE", ajeno), null).statusCode()).isEqualTo(403);
    }

    @Test
    void elClienteNoProgramaNiConsultaNiCancelaYSinSesionEs401() throws Exception {
        long suyo = cliente();
        long o = declarada(suyo);
        String ruta = "/api/tributario/obligaciones/" + o + "/recordatorios-pago";
        String cliente = token("CLIENTE_MYPE", suyo);

        assertThat(llamar("POST", ruta, cliente, fecha(HOY)).statusCode()).isEqualTo(403);
        assertThat(llamar("GET", ruta, cliente, null).statusCode()).isEqualTo(403);
        assertThat(llamar("PATCH", ruta + "/1/cancelacion", cliente, null).statusCode()).isEqualTo(403);
        assertThat(llamar("POST", ruta, null, fecha(HOY)).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.recordatorios_pago WHERE obligacion_id = ?", Integer.class, o)).isZero();
    }

    @Test
    void siSePagaAntesDeSuDiaNoLlegaAlClienteYLosErroresSalenComo400o404() throws Exception {
        long suyo = cliente();
        long o = declarada(suyo);
        String ruta = "/api/tributario/obligaciones/" + o + "/recordatorios-pago";
        String contador = token("CONTADOR", null);

        assertThat(llamar("POST", ruta, contador, fecha(HOY.minusDays(1))).statusCode()).isEqualTo(400);
        assertThat(llamar("POST", ruta, contador, fecha(HOY.plusDays(11))).statusCode()).isEqualTo(400);
        assertThat(llamar("POST", ruta, contador, "{}").statusCode()).isEqualTo(400);
        assertThat(llamar("POST", "/api/tributario/obligaciones/-1/recordatorios-pago", contador, fecha(HOY)).statusCode()).isEqualTo(404);

        assertThat(llamar("POST", ruta, contador, fecha(HOY)).statusCode()).isEqualTo(201);
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues)
                VALUES (?, ?, 1200.00, ?, 1200.00, 0)""", o, HOY, "C-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGADO' WHERE id = ?", o);

        recordatorios.entregar(HOY);

        assertThat(json.readTree(llamar("GET", "/api/tributario/clientes/" + suyo + "/avisos-pago", token("CLIENTE_MYPE", suyo), null).body())).isEmpty();
        assertThat(json.readTree(llamar("GET", ruta, contador, null).body()).get(0).get("estado").asText()).isEqualTo("OMITIDO");
    }

    @Test
    void conPagoParcialElContadorProgramaYElClienteRecibeElSaldoPendiente() throws Exception {
        long suyo = cliente();
        long o = declarada(suyo);                                     // monto determinado 1200.00
        jdbc.update("""
                INSERT INTO tributario_schema.pagos_obligacion(obligacion_id, fecha_pago, monto_pagado, numero_constancia, acumulado_despues, saldo_despues)
                VALUES (?, ?, 200.00, 'C-PARCIAL-1', 200.00, 1000.00)""", o, HOY);
        jdbc.update("UPDATE tributario_schema.obligaciones SET estado = 'PAGO_PARCIAL' WHERE id = ?", o);
        String ruta = "/api/tributario/obligaciones/" + o + "/recordatorios-pago";

        var creado = llamar("POST", ruta, token("CONTADOR", null), fecha(HOY));
        assertThat(creado.statusCode()).isEqualTo(201);
        assertThat(json.readTree(creado.body()).get("saldo").decimalValue()).isEqualByComparingTo("1000.00");

        recordatorios.entregar(HOY);

        JsonNode lista = json.readTree(llamar("GET", "/api/tributario/clientes/" + suyo + "/avisos-pago", token("CLIENTE_MYPE", suyo), null).body());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("monto").decimalValue()).isEqualByComparingTo("1000.00");   // el saldo, no el monto determinado
    }
}
