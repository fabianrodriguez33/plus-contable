package com.pluscontable.tributario.compras;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-25 · T216, de punta a punta por HTTP real con PostgreSQL desechable: el contador carga una compra, el cliente la ve sin clasificar y
 * la clasifica con una descripción, el contador la ve ya clasificada, y otra empresa no ve ni toca nada. Clientes y compras ficticios que
 * se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CompraHttpTest {

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> clientes = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : clientes) jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
    }

    private long cliente() {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        Long id = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario)
                VALUES (?, 'Cliente HU25 HTTP SAC', 'MYPE') RETURNING id""", Long.class, ruc);
        clientes.add(id);
        return id;
    }

    private String token(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject(clienteId == null ? "contador1" : "cliente" + clienteId).claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> llamar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String compra(String comprobante, LocalDate periodo) {
        return """
                {"periodo":"%s","proveedorRuc":"20100070970","proveedorRazonSocial":"Proveedor Prueba SAC","comprobante":"%s",
                 "fechaComprobante":"%s","importe":1180.00}""".formatted(periodo, comprobante, periodo.plusDays(4));
    }

    @Test
    void elContadorCargaElClienteClasificaYElContadorLaVeClasificada() throws Exception {
        long suyo = cliente();
        String ruta = "/api/tributario/clientes/" + suyo + "/compras";
        String contador = token("CONTADOR", null);
        String cliente = token("CLIENTE_MYPE", suyo);
        // periodo = mes de hoy en Lima: es el que cierra pronto o ya cerró según el día
        LocalDate periodo = LocalDate.now(ZoneId.of("America/Lima")).withDayOfMonth(1);

        var creada = llamar("POST", ruta, contador, compra("F001-" + UUID.randomUUID().toString().substring(0, 6), periodo));
        assertThat(creada.statusCode()).isEqualTo(201);
        long compraId = json.readTree(creada.body()).get("id").asLong();
        assertThat(json.readTree(creada.body()).get("clasificada").asBoolean()).isFalse();

        JsonNode vista = json.readTree(llamar("GET", ruta, cliente, null).body());
        assertThat(vista).hasSize(1);
        assertThat(vista.get(0).get("id").asLong()).isEqualTo(compraId);
        assertThat(vista.get(0).get("clasificada").asBoolean()).isFalse();
        assertThat(vista.get(0).get("importe").decimalValue()).isEqualByComparingTo("1180.00");

        var clasificada = llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente,
                "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"Laptop del área contable\"}");
        assertThat(clasificada.statusCode()).isEqualTo(200);

        JsonNode delContador = json.readTree(llamar("GET", ruta + "?clasificada=true", contador, null).body());
        assertThat(delContador).hasSize(1);
        assertThat(delContador.get(0).get("tipo").asText()).isEqualTo("ACTIVO_FIJO");
        assertThat(delContador.get(0).get("descripcion").asText()).isEqualTo("Laptop del área contable");
        assertThat(delContador.get(0).get("alertaCierre").asBoolean()).isFalse();    // clasificada: no avisa
        assertThat(json.readTree(llamar("GET", ruta + "?clasificada=false", contador, null).body())).isEmpty();
        // una clasificada no se cambia
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"MERCADERIA_O_GASTO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(400);
    }

    @Test
    void otraEmpresaNoVeNiClasificaYElClienteNoCargaNiElContadorClasifica() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        String ruta = "/api/tributario/clientes/" + suyo + "/compras";
        var creada = llamar("POST", ruta, token("CONTADOR", null), compra("F001-" + UUID.randomUUID().toString().substring(0, 6), LocalDate.of(2026, 11, 1)));
        long compraId = json.readTree(creada.body()).get("id").asLong();
        String delAjeno = token("CLIENTE_MYPE", ajeno);

        assertThat(llamar("GET", ruta, delAjeno, null).statusCode()).isEqualTo(403);
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", delAjeno, "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(403);
        // el mismo id de compra pedido por el cliente correcto pero dentro de la ruta de otra empresa: 403 (no su empresa)
        assertThat(llamar("PATCH", "/api/tributario/clientes/" + ajeno + "/compras/" + compraId + "/clasificacion", delAjeno,
                "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(404);
        assertThat(llamar("POST", ruta, token("CLIENTE_MYPE", suyo), compra("F001-999", LocalDate.of(2026, 11, 1))).statusCode()).isEqualTo(403);
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", token("CONTADOR", null), "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(403);
        assertThat(llamar("GET", ruta, null, null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT tipo_clasificacion IS NULL FROM tributario_schema.compras_cliente WHERE id = ?", Boolean.class, compraId)).isTrue();
    }

    @Test
    void siFaltaLaDescripcionOElTipoEsInvalidoResponde400() throws Exception {
        long suyo = cliente();
        String ruta = "/api/tributario/clientes/" + suyo + "/compras";
        long compraId = json.readTree(llamar("POST", ruta, token("CONTADOR", null),
                compra("F001-" + UUID.randomUUID().toString().substring(0, 6), LocalDate.of(2026, 11, 1))).body()).get("id").asLong();
        String cliente = token("CLIENTE_MYPE", suyo);

        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"ACTIVO_FIJO\"}").statusCode()).isEqualTo(400);
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"OTRO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(400);
        assertThat(llamar("PATCH", ruta + "/-1/clasificacion", cliente, "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(404);
    }
}
