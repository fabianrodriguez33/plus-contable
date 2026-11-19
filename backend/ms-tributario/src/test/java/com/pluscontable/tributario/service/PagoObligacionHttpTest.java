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
 * HU-13 (tarea 11): registrar un pago por HTTP real, sin archivo (el comprobante es el número de constancia), y comprobar que la
 * constancia queda asociada a la obligación y se puede consultar. PostgreSQL real; cliente y obligación ficticios que se borran.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PagoObligacionHttpTest {

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String ruc;
    private long obligacion;
    private long otraObligacion;

    @BeforeEach
    void crear() {
        ruc = RucFicticio.nuevo();
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, 'Cliente ficticio (pagos HTTP)', 'RER')", ruc);
        long cliente = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
        obligacion = nueva(cliente, "IGV");
        otraObligacion = nueva(cliente, "RENTA");
    }

    @AfterEach
    void borrar() {
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long nueva(long cliente, String tipo) {
        jdbc.update("""
                INSERT INTO tributario_schema.obligaciones (cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, fecha_declaracion, numero_formulario, monto_determinado)
                VALUES (?, ?, '2026-10-01', '2026-11-20', 'DECLARADO', '2026-11-05', 'F-1', 1000.00)
                """, cliente, tipo);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.obligaciones WHERE cliente_id = ? AND tipo_obligacion = ?", Long.class, cliente, tipo);
    }

    private String token(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("contador1").claim("roles", List.of(rol)).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> obtener(long id, String rol) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/tributario/obligaciones/" + id + "/pagos")).GET();
        if (rol != null) b.header("Authorization", "Bearer " + token(rol));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Formulario multipart sin archivo, como lo envía la pantalla. */
    private HttpResponse<String> pagar(long id, String fecha, String monto, String constancia) throws Exception {
        String frontera = "----pc" + UUID.randomUUID();
        StringBuilder cuerpo = new StringBuilder();
        for (String[] campo : new String[][] {{"fechaPago", fecha}, {"montoPagado", monto}, {"numeroConstancia", constancia}}) {
            cuerpo.append("--").append(frontera).append("\r\nContent-Disposition: form-data; name=\"").append(campo[0]).append("\"\r\n\r\n")
                    .append(campo[1]).append("\r\n");
        }
        cuerpo.append("--").append(frontera).append("--\r\n");
        var r = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/tributario/obligaciones/" + id + "/pagos"))
                .header("Authorization", "Bearer " + token("CONTADOR")).header("Content-Type", "multipart/form-data; boundary=" + frontera)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo.toString())).build();
        return http.send(r, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void elPagoSinArchivoSeRegistraYSuConstanciaQuedaAsociadaALaObligacionYSePuedeConsultar() throws Exception {
        HttpResponse<String> r = pagar(obligacion, "2026-11-08", "400.00", "C-001");

        assertThat(r.statusCode()).isEqualTo(201);
        JsonNode o = json.readTree(r.body());
        assertThat(o.get("estado").asText()).isEqualTo("PAGO_PARCIAL");
        assertThat(o.get("montoAcumuladoPagado").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(o.get("saldo").decimalValue()).isEqualByComparingTo("600.00");

        JsonNode lista = json.readTree(obtener(obligacion, "CONTADOR").body());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("numeroConstancia").asText()).isEqualTo("C-001");
        assertThat(lista.get(0).get("fechaPago").asText()).isEqualTo("2026-11-08");
        assertThat(lista.get(0).get("saldoDespues").decimalValue()).isEqualByComparingTo("600.00");
        // la constancia es de esta obligación y no de la otra
        assertThat(json.readTree(obtener(otraObligacion, "CONTADOR").body())).isEmpty();
        // sin archivo no queda nada guardado
        assertThat(jdbc.queryForObject("SELECT ruta_almacenamiento FROM tributario_schema.pagos_obligacion WHERE obligacion_id = ?", String.class, obligacion)).isNull();
    }

    @Test
    void variosPagosParcialesAcumulanHastaPagadoYLaListaSeDevuelveEnOrden() throws Exception {
        assertThat(pagar(obligacion, "2026-11-02", "400.00", "C-001").statusCode()).isEqualTo(201);
        JsonNode segundo = json.readTree(pagar(obligacion, "2026-11-05", "350.00", "C-002").body());
        assertThat(segundo.get("montoAcumuladoPagado").decimalValue()).isEqualByComparingTo("750.00");
        assertThat(segundo.get("numeroPagos").asInt()).isEqualTo(2);
        JsonNode tercero = json.readTree(pagar(obligacion, "2026-11-08", "250.00", "C-003").body());
        assertThat(tercero.get("estado").asText()).isEqualTo("PAGADO");
        assertThat(tercero.get("saldo").decimalValue()).isEqualByComparingTo("0");

        JsonNode lista = json.readTree(obtener(obligacion, "CONTADOR").body());
        assertThat(lista).hasSize(3);
        assertThat(List.of(lista.get(0).get("numeroConstancia").asText(), lista.get(1).get("numeroConstancia").asText(), lista.get(2).get("numeroConstancia").asText()))
                .containsExactly("C-001", "C-002", "C-003");
        assertThat(lista.get(2).get("acumuladoDespues").decimalValue()).isEqualByComparingTo("1000.00");
        // una obligación pagada ya no recibe más pagos
        HttpResponse<String> otro = pagar(obligacion, LocalDate.of(2026, 11, 9).toString(), "1.00", "C-004");
        assertThat(otro.statusCode()).isEqualTo(400);
        assertThat(json.readTree(otro.body()).get("mensaje").asText()).isEqualTo("La obligación ya está pagada");
    }

    @Test
    void laConstanciaRepetidaYElMontoMayorQueElSaldoSeRechazanSinGuardarNada() throws Exception {
        pagar(obligacion, "2026-11-08", "400.00", "C-001");

        HttpResponse<String> repetida = pagar(obligacion, "2026-11-08", "100.00", "C-001");
        HttpResponse<String> excede = pagar(obligacion, "2026-11-08", "600.01", "C-002");

        assertThat(repetida.statusCode()).isEqualTo(400);
        assertThat(json.readTree(repetida.body()).get("mensaje").asText()).contains("numeroConstancia (ya registrado en esta obligación)");
        assertThat(excede.statusCode()).isEqualTo(400);
        assertThat(json.readTree(excede.body()).get("mensaje").asText()).contains("supera el saldo pendiente de 600.00");
        assertThat(json.readTree(obtener(obligacion, "CONTADOR").body())).hasSize(1);
    }

    @Test
    void consultarLosPagosExigeSerDelEstudioYLaObligacionDebeExistir() throws Exception {
        assertThat(obtener(obligacion, null).statusCode()).isEqualTo(401);
        assertThat(obtener(obligacion, "CLIENTE_MYPE").statusCode()).isEqualTo(403);
        assertThat(obtener(obligacion, "ADMIN").statusCode()).isEqualTo(200);
        assertThat(obtener(0L, "CONTADOR").statusCode()).isEqualTo(404);
    }
}
