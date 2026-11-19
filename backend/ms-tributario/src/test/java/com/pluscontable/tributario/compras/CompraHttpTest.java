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
 * propone la clasificación con una descripción, el contador la valida, y otra empresa no ve ni toca nada. Clientes y compras ficticios que
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
    void elContadorCargaElClienteProponeYElContadorValida() throws Exception {
        long suyo = cliente();
        String ruta = "/api/tributario/clientes/" + suyo + "/compras";
        String contador = token("CONTADOR", null);
        String cliente = token("CLIENTE_MYPE", suyo);
        LocalDate periodo = LocalDate.now(ZoneId.of("America/Lima")).withDayOfMonth(1);

        var creada = llamar("POST", ruta, contador, compra("F001-" + UUID.randomUUID().toString().substring(0, 6), periodo));
        assertThat(creada.statusCode()).isEqualTo(201);
        long compraId = json.readTree(creada.body()).get("id").asLong();
        assertThat(json.readTree(creada.body()).get("estado").asText()).isEqualTo("SIN_CLASIFICAR");

        JsonNode vista = json.readTree(llamar("GET", ruta, cliente, null).body());
        assertThat(vista).hasSize(1);
        assertThat(vista.get(0).get("estado").asText()).isEqualTo("SIN_CLASIFICAR");
        assertThat(vista.get(0).get("importe").decimalValue()).isEqualByComparingTo("1180.00");
        assertThat(vista.get(0).get("alertaCierre").asBoolean()).isFalse();           // la alerta es solo del contador

        // validar antes de que el cliente proponga: 400
        assertThat(llamar("POST", ruta + "/" + compraId + "/validacion", contador, null).statusCode()).isEqualTo(400);

        var propuesta = llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"Laptop del área contable\"}");
        assertThat(propuesta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(propuesta.body()).get("estado").asText()).isEqualTo("PROPUESTA");
        // el cliente puede cambiar su propuesta mientras no se valide
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"MERCADERIA_O_GASTO\",\"descripcion\":\"Insumos\"}").statusCode()).isEqualTo(200);

        JsonNode delContador = json.readTree(llamar("GET", ruta + "?estado=PROPUESTA", contador, null).body());
        assertThat(delContador).hasSize(1);
        assertThat(delContador.get(0).get("tipo").asText()).isEqualTo("MERCADERIA_O_GASTO");
        assertThat(delContador.get(0).get("descripcion").asText()).isEqualTo("Insumos");

        var validada = llamar("POST", ruta + "/" + compraId + "/validacion", contador, null);
        assertThat(validada.statusCode()).isEqualTo(200);
        assertThat(json.readTree(validada.body()).get("estado").asText()).isEqualTo("VALIDADA");
        assertThat(json.readTree(validada.body()).get("validadaPor").asText()).isEqualTo("contador1");
        assertThat(json.readTree(llamar("GET", ruta + "?estado=VALIDADA", contador, null).body())).hasSize(1);
        assertThat(json.readTree(llamar("GET", ruta + "?estado=PROPUESTA", contador, null).body())).isEmpty();
        // validada: el cliente ya no la cambia y el contador no la valida dos veces; el cliente no ve quién validó
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", cliente, "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}").statusCode()).isEqualTo(400);
        assertThat(llamar("POST", ruta + "/" + compraId + "/validacion", contador, null).statusCode()).isEqualTo(400);
        JsonNode delCliente = json.readTree(llamar("GET", ruta, cliente, null).body());
        assertThat(delCliente.get(0).get("estado").asText()).isEqualTo("VALIDADA");
        assertThat(delCliente.get(0).has("validadaPor") && !delCliente.get(0).get("validadaPor").isNull()).isFalse();
    }

    @Test
    void otraEmpresaNoVeNiProponeNiValidaYElClienteNoCargaNiValidaYElContadorNoPropone() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        String ruta = "/api/tributario/clientes/" + suyo + "/compras";
        var creada = llamar("POST", ruta, token("CONTADOR", null), compra("F001-" + UUID.randomUUID().toString().substring(0, 6), LocalDate.of(2026, 11, 1)));
        long compraId = json.readTree(creada.body()).get("id").asLong();
        String delAjeno = token("CLIENTE_MYPE", ajeno);
        String suyoToken = token("CLIENTE_MYPE", suyo);
        String propuesta = "{\"tipo\":\"ACTIVO_FIJO\",\"descripcion\":\"x\"}";

        assertThat(llamar("GET", ruta, delAjeno, null).statusCode()).isEqualTo(403);
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", delAjeno, propuesta).statusCode()).isEqualTo(403);
        // el id de compra de otra empresa pedido dentro de la ruta de la propia empresa: 404
        assertThat(llamar("PATCH", "/api/tributario/clientes/" + ajeno + "/compras/" + compraId + "/clasificacion", delAjeno, propuesta).statusCode()).isEqualTo(404);
        assertThat(llamar("POST", "/api/tributario/clientes/" + ajeno + "/compras/" + compraId + "/validacion", token("CONTADOR", null), null).statusCode()).isEqualTo(404);
        assertThat(llamar("POST", ruta, suyoToken, compra("F001-999", LocalDate.of(2026, 11, 1))).statusCode()).isEqualTo(403);
        assertThat(llamar("POST", ruta + "/" + compraId + "/validacion", suyoToken, null).statusCode()).isEqualTo(403);
        assertThat(llamar("PATCH", ruta + "/" + compraId + "/clasificacion", token("CONTADOR", null), propuesta).statusCode()).isEqualTo(403);
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
