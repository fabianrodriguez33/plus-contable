package com.pluscontable.tributario.service;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
 * HU-14 (tarea 33): el tablero Declarado vs. Pagado de punta a punta con HTTP y PostgreSQL reales. Los pagos se registran por
 * el endpoint de HU-13, así que PAGO_PARCIAL y PAGADO son los estados reales. Clientes ficticios que se borran al terminar;
 * las fechas son relativas al «hoy» de Lima, que es el que usa el servicio.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TableroIntegracionTest {

    private static final String BASE = "/api/tributario/tablero";

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final LocalDate hoy = LocalDate.now(TableroService.ZONA);
    private final LocalDate mes = hoy.withDayOfMonth(1);
    private final LocalDate mesAnterior = mes.minusMonths(1);
    private final List<String> rucs = new ArrayList<>();

    private long clienteA;
    private long clienteB;
    private long igvPendienteHoy;       // A, mes, vence hoy, PENDIENTE
    private long rentaPendienteAyer;    // A, mes anterior, venció ayer, PENDIENTE
    private long plameDeclarada;        // A, mes, venció hace 3 días, DECLARADO sin pago
    private long essaludPagada;         // A, mes, venció hace 3 días, DECLARADO y pagada completa
    private long igvParcial;            // B, mes, venció hace 2 días, DECLARADO con un pago parcial
    private long rentaDeclaradaFutura;  // B, mes, vence en 10 días, DECLARADO sin pago

    @BeforeEach
    void crear() throws Exception {
        clienteA = cliente("Aaa cliente ficticio (tablero)");
        clienteB = cliente("Bbb cliente ficticio (tablero)");
        igvPendienteHoy = obligacion(clienteA, "IGV", mes, hoy, "PENDIENTE", null);
        rentaPendienteAyer = obligacion(clienteA, "RENTA", mesAnterior, hoy.minusDays(1), "PENDIENTE", null);
        plameDeclarada = obligacion(clienteA, "PLAME", mes, hoy.minusDays(3), "DECLARADO", "1000.00");
        essaludPagada = obligacion(clienteA, "ESSALUD", mes, hoy.minusDays(3), "DECLARADO", "800.00");
        igvParcial = obligacion(clienteB, "IGV", mes, hoy.minusDays(2), "DECLARADO", "1000.00");
        rentaDeclaradaFutura = obligacion(clienteB, "RENTA", mes, hoy.plusDays(10), "DECLARADO", "300.00");
        assertThat(pagar(essaludPagada, "800.00", "C-ESS").statusCode()).isEqualTo(201);
        assertThat(pagar(igvParcial, "250.00", "C-IGV1").statusCode()).isEqualTo(201); // dos pagos: lo pagado es la suma, 400
        assertThat(pagar(igvParcial, "150.00", "C-IGV2").statusCode()).isEqualTo(201);
    }

    @AfterEach
    void borrar() {
        for (String ruc : rucs) jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long cliente(String razon) {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, ?, 'RER')", ruc, razon);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private long obligacion(long cliente, String tipo, LocalDate periodo, LocalDate vence, String estado, String monto) {
        jdbc.update("""
                INSERT INTO tributario_schema.obligaciones (cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, cliente, tipo, periodo, vence, estado, "DECLARADO".equals(estado) ? hoy.minusDays(20) : null,
                "DECLARADO".equals(estado) ? "F-1" : null, monto == null ? null : new java.math.BigDecimal(monto));
        return jdbc.queryForObject("SELECT id FROM tributario_schema.obligaciones WHERE cliente_id = ? AND tipo_obligacion = ? AND periodo = ?",
                Long.class, cliente, tipo, periodo);
    }

    private String token(String... roles) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("contador1").claim("roles", List.of(roles)).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    /** Pago por el endpoint de HU-13 (formulario multipart sin archivo, como lo envía la pantalla). */
    private HttpResponse<String> pagar(long obligacion, String monto, String constancia) throws Exception {
        String frontera = "----pc" + UUID.randomUUID();
        StringBuilder cuerpo = new StringBuilder();
        for (String[] campo : new String[][] {{"fechaPago", hoy.minusDays(1).toString()}, {"montoPagado", monto}, {"numeroConstancia", constancia}}) {
            cuerpo.append("--").append(frontera).append("\r\nContent-Disposition: form-data; name=\"").append(campo[0]).append("\"\r\n\r\n")
                    .append(campo[1]).append("\r\n");
        }
        cuerpo.append("--").append(frontera).append("--\r\n");
        var r = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/tributario/obligaciones/" + obligacion + "/pagos"))
                .header("Authorization", "Bearer " + token("CONTADOR")).header("Content-Type", "multipart/form-data; boundary=" + frontera)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();
        return http.send(r, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> tablero(String consulta, String... roles) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + BASE + consulta)).GET();
        if (roles.length > 0) b.header("Authorization", "Bearer " + token(roles));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode filas(String consulta) throws Exception {
        HttpResponse<String> r = tablero(consulta, "CONTADOR");
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body());
    }

    private List<Long> ids(JsonNode filas) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode f : filas) ids.add(f.get("obligacionId").asLong());
        return ids;
    }

    private JsonNode fila(JsonNode filas, long obligacion) {
        for (JsonNode f : filas) if (f.get("obligacionId").asLong() == obligacion) return f;
        throw new AssertionError("la obligación " + obligacion + " no está en el tablero");
    }

    // ---- criterio 1: cada obligación con su estado real

    @Test
    void criterio1_cadaObligacionSaleConSuEstadoRealIncluidosElPagoParcialYElPagado() throws Exception {
        JsonNode todas = filas("?periodo=" + mes);

        assertThat(fila(todas, igvPendienteHoy).get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(fila(todas, plameDeclarada).get("estado").asText()).isEqualTo("DECLARADO");
        assertThat(fila(todas, essaludPagada).get("estado").asText()).isEqualTo("PAGADO");
        assertThat(fila(todas, igvParcial).get("estado").asText()).isEqualTo("PAGO_PARCIAL");
        assertThat(fila(todas, igvParcial).get("razonSocial").asText()).startsWith("Bbb");
        assertThat(fila(todas, igvParcial).get("tipoObligacion").asText()).isEqualTo("IGV");
    }

    // ---- criterio 2: por cliente y por mes

    @Test
    void criterio2_sePuedeVerPorClientePorMesOPorLosDos() throws Exception {
        assertThat(ids(filas("?clienteId=" + clienteA))).containsExactlyInAnyOrder(igvPendienteHoy, rentaPendienteAyer, plameDeclarada, essaludPagada);
        assertThat(ids(filas("?clienteId=" + clienteB))).containsExactlyInAnyOrder(igvParcial, rentaDeclaradaFutura);
        assertThat(ids(filas("?clienteId=" + clienteA + "&periodo=" + mesAnterior))).containsExactly(rentaPendienteAyer);
        assertThat(ids(filas("?clienteId=" + clienteA + "&periodo=" + mes))).containsExactlyInAnyOrder(igvPendienteHoy, plameDeclarada, essaludPagada);
        // sin filtro de cliente: las de los dos clientes en ese mes
        assertThat(ids(filas("?periodo=" + mes))).contains(igvPendienteHoy, plameDeclarada, essaludPagada, igvParcial, rentaDeclaradaFutura)
                .doesNotContain(rentaPendienteAyer);
        assertThat(ids(filas("?periodo=" + mes.minusMonths(6)))).doesNotContain(igvPendienteHoy, igvParcial);
    }

    @Test
    void criterio2_unClienteInexistenteDa404UnPeriodoQueNoEsPrimerDiaOUnClienteNoPositivoDa400() throws Exception {
        assertThat(tablero("?clienteId=" + Long.MAX_VALUE, "CONTADOR").statusCode()).isEqualTo(404);
        HttpResponse<String> mal = tablero("?periodo=" + mes.plusDays(1), "CONTADOR");
        assertThat(mal.statusCode()).isEqualTo(400);
        assertThat(mal.body()).contains("primer día del mes");
        assertThat(tablero("?clienteId=0", "CONTADOR").statusCode()).isEqualTo(400);
        assertThat(tablero("?periodo=2026-13-45", "CONTADOR").statusCode()).isEqualTo(400);
    }

    // ---- criterio 3: monto y fecha límite

    @Test
    void criterio3_cadaObligacionMuestraSuMontoSuFechaLimiteLoPagadoYElSaldo() throws Exception {
        JsonNode todas = filas("?periodo=" + mes);

        JsonNode parcial = fila(todas, igvParcial);
        assertThat(parcial.get("montoDeterminado").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(parcial.get("montoPagado").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(parcial.get("saldo").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(parcial.get("fechaVencimiento").asText()).isEqualTo(hoy.minusDays(2).toString());
        JsonNode pagada = fila(todas, essaludPagada);
        assertThat(pagada.get("saldo").decimalValue()).isEqualByComparingTo("0");
        JsonNode sinPago = fila(todas, plameDeclarada);
        assertThat(sinPago.get("montoPagado").decimalValue()).isEqualByComparingTo("0");
        assertThat(sinPago.get("saldo").decimalValue()).isEqualByComparingTo("1000.00");
        // una pendiente aún sin monto determinado: monto y saldo vacíos, no cero
        JsonNode pendiente = fila(todas, igvPendienteHoy);
        assertThat(pendiente.get("montoDeterminado").isNull()).isTrue();
        assertThat(pendiente.get("saldo").isNull()).isTrue();
        assertThat(pendiente.get("fechaVencimiento").asText()).isEqualTo(hoy.toString());
    }

    // ---- criterio 4: filtrar «declarado sin pago»

    @Test
    void criterio4_declaradoSinPagoMuestraSoloLasDeclaradasQueNoTienenNingunPago() throws Exception {
        JsonNode sinPago = filas("?soloDeclaradoSinPago=true&periodo=" + mes);

        assertThat(ids(sinPago)).contains(plameDeclarada, rentaDeclaradaFutura)
                .doesNotContain(essaludPagada, igvParcial, igvPendienteHoy, rentaPendienteAyer);
        for (JsonNode f : sinPago) {
            assertThat(f.get("estado").asText()).isEqualTo("DECLARADO");
            assertThat(f.get("montoPagado").decimalValue()).isEqualByComparingTo("0");
        }
        assertThat(ids(filas("?soloDeclaradoSinPago=true&clienteId=" + clienteB))).containsExactly(rentaDeclaradaFutura);
        assertThat(ids(filas("?soloDeclaradoSinPago=false&clienteId=" + clienteB))).containsExactlyInAnyOrder(igvParcial, rentaDeclaradaFutura);
    }

    // ---- «Vencida» solo para lo que todavía se debe

    @Test
    void vencidaSoloParaPendientesYPagosParcialesConFechaAnteriorAHoyYNuncaParaLasPagadas() throws Exception {
        JsonNode todas = filas("?clienteId=" + clienteA);
        JsonNode deB = filas("?clienteId=" + clienteB);

        assertThat(fila(todas, igvPendienteHoy).get("vencida").asBoolean()).isFalse();      // vence hoy: todavía no
        assertThat(fila(todas, rentaPendienteAyer).get("vencida").asBoolean()).isTrue();    // venció ayer
        assertThat(fila(todas, essaludPagada).get("vencida").asBoolean()).isFalse();        // pagada con fecha pasada
        assertThat(fila(todas, plameDeclarada).get("vencida").asBoolean()).isFalse();       // declarada: misma regla del calendario
        assertThat(fila(deB, igvParcial).get("vencida").asBoolean()).isTrue();              // todavía debe 600
        assertThat(fila(deB, rentaDeclaradaFutura).get("vencida").asBoolean()).isFalse();
    }

    @Test
    void lasFilasVienenPorFechaLimiteClienteYTributo() throws Exception {
        List<Long> orden = ids(filas("?clienteId=" + clienteA));

        assertThat(orden).containsExactly(essaludPagada, plameDeclarada, rentaPendienteAyer, igvPendienteHoy);
    }

    // ---- criterio 5: solo el contador autenticado

    @Test
    void criterio5_sinSesion401UnClienteMypeOUnTokenSinRol403ElContadorYElAdministradorVenElTablero() throws Exception {
        assertThat(tablero("?clienteId=" + clienteA).statusCode()).isEqualTo(401);
        assertThat(tablero("?clienteId=" + clienteA, "CLIENTE_MYPE").statusCode()).isEqualTo(403);
        assertThat(tablero("?clienteId=" + clienteA, "OTRO_ROL").statusCode()).isEqualTo(403);
        assertThat(tablero("?clienteId=" + clienteA, "CONTADOR").statusCode()).isEqualTo(200);
        assertThat(tablero("?clienteId=" + clienteA, "ADMIN").statusCode()).isEqualTo(200);
        assertThat(tablero("?clienteId=" + clienteA, "CLIENTE_MYPE").body()).doesNotContain("razonSocial");
    }

    @Test
    void elTableroSoloLeeNoCambiaNingunPagoNiObligacion() throws Exception {
        Integer pagosAntes = jdbc.queryForObject("SELECT count(*) FROM tributario_schema.pagos_obligacion", Integer.class);
        String estadosAntes = jdbc.queryForObject("SELECT string_agg(id || estado, ',' ORDER BY id) FROM tributario_schema.obligaciones WHERE cliente_id IN (?, ?)",
                String.class, clienteA, clienteB);

        filas("");
        filas("?soloDeclaradoSinPago=true");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tributario_schema.pagos_obligacion", Integer.class)).isEqualTo(pagosAntes);
        assertThat(jdbc.queryForObject("SELECT string_agg(id || estado, ',' ORDER BY id) FROM tributario_schema.obligaciones WHERE cliente_id IN (?, ?)",
                String.class, clienteA, clienteB)).isEqualTo(estadosAntes);
    }
}
