package com.pluscontable.tributario.empresa;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
 * HU-32 · T201, de punta a punta por HTTP real con PostgreSQL desechable: lo que consulta la pantalla «Mi estado de cuenta»
 * (GET /obligaciones?clienteId=) con el token de una cuenta de cliente. Ve los tributos de su empresa con estado, monto y fecha
 * límite; nunca los de otra empresa (403, también al pedir una obligación suelta); sin sesión 401; el estudio ve ambas.
 * Clientes y obligaciones ficticios que se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EstadoCuentaClienteHttpTest {

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final AtomicLong SECUENCIA = new AtomicLong(System.nanoTime() % 100_000_000L);

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
                VALUES (?, 'Cliente HU32 HTTP SAC', 'MYPE') RETURNING id""", Long.class, rucFicticio());
        clientes.add(id);
        return id;
    }

    private long obligacion(long cliente, String tipo, String periodo, String vence, String estado, String monto) {
        return jdbc.queryForObject("""
                INSERT INTO tributario_schema.obligaciones(cliente_id, tipo_obligacion, periodo, fecha_vencimiento, estado, monto_determinado)
                VALUES (?, ?, ?::date, ?::date, ?, ?::numeric) RETURNING id""", Long.class, cliente, tipo, periodo, vence, estado, monto);
    }

    /** Como ms-auth: el claim clienteId solo va en el token de una cuenta de cliente. */
    private String token(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> get(String ruta, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void elClienteVeLosTributosDeSuEmpresaConEstadoMontoYFechaLimite() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        long igv = obligacion(suyo, "IGV", "2026-10-01", "2026-11-18", "DECLARADO", "1500.50");
        long renta = obligacion(suyo, "RENTA", "2026-10-01", "2026-11-18", "PAGO_PARCIAL", "800.00");
        obligacion(ajeno, "IGV", "2026-10-01", "2026-11-18", "PENDIENTE", "99.00");

        var r = get("/api/tributario/obligaciones?clienteId=" + suyo, token("CLIENTE_MYPE", suyo));

        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode lista = json.readTree(r.body());
        assertThat(lista).hasSize(2);
        for (JsonNode o : lista) {
            assertThat(o.get("clienteId").asLong()).isEqualTo(suyo);
            if (o.get("id").asLong() == igv) {
                assertThat(o.get("tipoObligacion").asText()).isEqualTo("IGV");
                assertThat(o.get("estado").asText()).isEqualTo("DECLARADO");
                assertThat(o.get("montoDeterminado").decimalValue()).isEqualByComparingTo("1500.50");
                assertThat(o.get("fechaVencimiento").asText()).isEqualTo("2026-11-18");
                assertThat(o.get("periodo").asText()).isEqualTo("2026-10-01");
            } else {
                assertThat(o.get("id").asLong()).isEqualTo(renta);
                assertThat(o.get("estado").asText()).isEqualTo("PAGO_PARCIAL");
            }
        }
    }

    @Test
    void elClienteNuncaVeLaEmpresaDeOtroNiUnaObligacionAjena() throws Exception {
        long suyo = cliente();
        long ajeno = cliente();
        obligacion(suyo, "IGV", "2026-10-01", "2026-11-18", "PENDIENTE", "10.00");
        long deAjeno = obligacion(ajeno, "IGV", "2026-10-01", "2026-11-18", "PENDIENTE", "99.00");
        String token = token("CLIENTE_MYPE", suyo);

        assertThat(get("/api/tributario/obligaciones?clienteId=" + ajeno, token).statusCode()).isEqualTo(403);
        assertThat(get("/api/tributario/obligaciones/" + deAjeno, token).statusCode()).isEqualTo(403);
    }

    @Test
    void unaCuentaSinEmpresaSinSesionOConOtroRolNoVeDatosDelCliente() throws Exception {
        long c = cliente();
        obligacion(c, "IGV", "2026-10-01", "2026-11-18", "PENDIENTE", "10.00");
        String ruta = "/api/tributario/obligaciones?clienteId=" + c;

        assertThat(get(ruta, token("CLIENTE_MYPE", null)).statusCode()).isEqualTo(403);   // cuenta sin claim clienteId
        assertThat(get(ruta, null).statusCode()).isEqualTo(401);
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            var r = get(ruta, token(rol, null));
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(json.readTree(r.body())).hasSize(1);
        }
    }
}
