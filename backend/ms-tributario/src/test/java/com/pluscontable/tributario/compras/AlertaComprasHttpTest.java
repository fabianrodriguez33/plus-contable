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
 * HU-25 · T212 por HTTP real con PostgreSQL desechable: la alerta de fin de mes aparece en la bandeja de avisos del contador y del
 * administrador y no la ve el cliente (403). Clientes, compras y avisos ficticios que se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AlertaComprasHttpTest {
    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;
    @Autowired AlertaComprasService alerta;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private Long cliente;

    @AfterEach
    void limpiar() {
        if (cliente == null) return;
        jdbc.update("DELETE FROM tributario_schema.avisos WHERE id IN (SELECT aviso_id FROM tributario_schema.avisos_clientes WHERE cliente_id = ?)", cliente);
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);
    }

    private String token(String rol, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol));
        if (clienteId != null) b.claim("clienteId", clienteId);
        return b.issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> bandeja(String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/tributario/avisos"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void elContadorYElAdminVenLaAlertaEnSuBandejaYElClienteNo() throws Exception {
        String ruc = "20" + String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        cliente = jdbc.queryForObject("INSERT INTO tributario_schema.clientes(ruc, razon_social, regimen_tributario) VALUES (?, 'Cliente T212 HTTP SAC', 'MYPE') RETURNING id",
                Long.class, ruc);
        jdbc.update("""
                INSERT INTO tributario_schema.compras_cliente(cliente_id, periodo, proveedor_ruc, comprobante, fecha_comprobante, importe, registrada_por)
                VALUES (?, '2026-11-01', '20100070970', 'F-T212', '2026-11-05', 10.00, 'contador1')""", cliente);

        alerta.generar(LocalDate.of(2026, 11, 27));

        for (String rol : List.of("CONTADOR", "ADMIN")) {
            var r = bandeja(token(rol, null));
            assertThat(r.statusCode()).isEqualTo(200);
            boolean aparece = false;
            for (JsonNode a : json.readTree(r.body())) {
                if (a.get("origen").asText().equals("HU25_COMPRAS") && a.get("clienteIds").toString().contains(String.valueOf(cliente))) {
                    aparece = true;
                    assertThat(a.get("mensaje").asText()).contains("Cliente T212 HTTP SAC").contains("compras sin validar");
                }
            }
            assertThat(aparece).as("aviso visible para " + rol).isTrue();
        }
        assertThat(bandeja(token("CLIENTE_MYPE", cliente)).statusCode()).isEqualTo(403);
        assertThat(bandeja(token("CLIENTE_MYPE", cliente)).body()).doesNotContain("compras sin validar");
    }
}
