package com.pluscontable.tributario.ruc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-22 · T80 por HTTP real y PostgreSQL desechable: la edición de la ficha (PUT /clientes/{id}) que pasa a un cliente de
 * HABIDO a NO HABIDO deja un aviso interno en la bandeja del estudio. Clientes y avisos ficticios que se borran al terminar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AlertaNoHabidoHttpTest {

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final AtomicLong SECUENCIA = new AtomicLong(System.nanoTime() % 100_000_000L);

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;
    @SpyBean AvisoInternoService avisos;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<Long> clientes = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long id : clientes) {
            jdbc.update("""
                    DELETE FROM tributario_schema.avisos WHERE id IN
                    (SELECT aviso_id FROM tributario_schema.avisos_clientes WHERE cliente_id = ?)""", id);
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);
        }
    }

    private static String rucFicticio() {
        String cuerpo = "20" + String.format("%08d", SECUENCIA.incrementAndGet() % 100_000_000L);
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) suma += (cuerpo.charAt(i) - '0') * FACTORES[i];
        int dv = 11 - (suma % 11);
        return cuerpo + (dv == 10 ? 0 : dv == 11 ? 1 : dv);
    }

    private String token(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("usuario-prueba").claim("roles", List.of(rol)).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> llamar(String rol, String metodo, String ruta, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token(rol)).header("Content-Type", "application/json");
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cuerpo(String ruc, String razonSocial, String condicion) {
        return """
                {"ruc":"%s","razonSocial":"%s","regimenTributario":"MYPE","condicionHabido":"%s"}
                """.formatted(ruc, razonSocial, condicion);
    }

    private long crear(String ruc, String condicion) throws Exception {
        var r = llamar("CONTADOR", "POST", "/api/tributario/clientes", cuerpo(ruc, "Empresa Prueba T80 SAC", condicion));
        assertThat(r.statusCode()).as(r.body()).isIn(200, 201);
        long id = json.readTree(r.body()).get("id").asLong();
        clientes.add(id);
        return id;
    }

    private HttpResponse<String> editar(long id, String ruc, String razonSocial, String condicion) throws Exception {
        return llamar("CONTADOR", "PUT", "/api/tributario/clientes/" + id, cuerpo(ruc, razonSocial, condicion));
    }

    private int avisosDe(long clienteId) {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.avisos_clientes WHERE cliente_id = ?",
                Integer.class, clienteId);
    }

    private List<JsonNode> enBandeja(String rol, long clienteId) throws Exception {
        var r = llamar(rol, "GET", "/api/tributario/avisos", null);
        assertThat(r.statusCode()).isEqualTo(200);
        List<JsonNode> propios = new ArrayList<>();
        for (JsonNode aviso : json.readTree(r.body())) {
            for (JsonNode c : aviso.get("clienteIds")) if (c.asLong() == clienteId) propios.add(aviso);
        }
        return propios;
    }

    @Test
    void pasarANoHabidoCreaUnAvisoVisibleParaContadorYAdminPeroNoParaElCliente() throws Exception {
        String ruc = rucFicticio();
        long id = crear(ruc, "HABIDO");

        assertThat(editar(id, ruc, "Empresa Prueba T80 SAC", "NO_HABIDO").statusCode()).isEqualTo(200);

        assertThat(avisosDe(id)).isEqualTo(1);
        for (String rol : List.of("CONTADOR", "ADMIN")) {
            List<JsonNode> propios = enBandeja(rol, id);
            assertThat(propios).hasSize(1);
            assertThat(propios.getFirst().get("origen").asText()).isEqualTo("HU22_RUC");
            assertThat(propios.getFirst().get("claveEvento").asText()).startsWith("NO_HABIDO:cliente:" + id + ":");
            assertThat(propios.getFirst().get("mensaje").asText())
                    .contains("Empresa Prueba T80 SAC", ruc, "NO HABIDO");
        }
        assertThat(llamar("CLIENTE_MYPE", "GET", "/api/tributario/avisos", null).statusCode()).isEqualTo(403);
    }

    @Test
    void repetirLaEdicionOVolverACaerElMismoDiaNoDuplica() throws Exception {
        String ruc = rucFicticio();
        long id = crear(ruc, "HABIDO");
        assertThat(editar(id, ruc, "Empresa Prueba T80 SAC", "NO_HABIDO").statusCode()).isEqualTo(200);
        // Reintento idéntico: ya no hay transición.
        assertThat(editar(id, ruc, "Empresa Prueba T80 SAC", "NO_HABIDO").statusCode()).isEqualTo(200);
        // Recupera y vuelve a caer el mismo día con otra razón social: se conserva el aviso del día, sin error.
        assertThat(editar(id, ruc, "Empresa Prueba T80 SAC", "HABIDO").statusCode()).isEqualTo(200);
        assertThat(editar(id, ruc, "Empresa Renombrada T80 SAC", "NO_HABIDO").statusCode()).isEqualTo(200);

        assertThat(avisosDe(id)).isEqualTo(1);
    }

    @Test
    void sinCambioRecuperacionOAltaYaNoHabidaNoAvisan() throws Exception {
        String rucHabido = rucFicticio();
        long habido = crear(rucHabido, "HABIDO");
        assertThat(editar(habido, rucHabido, "Empresa Prueba T80 SAC", "HABIDO").statusCode()).isEqualTo(200);

        String rucNoHabido = rucFicticio();
        long noHabido = crear(rucNoHabido, "NO_HABIDO");
        assertThat(editar(noHabido, rucNoHabido, "Empresa Prueba T80 SAC", "NO_HABIDO").statusCode()).isEqualTo(200);
        assertThat(editar(noHabido, rucNoHabido, "Empresa Prueba T80 SAC", "HABIDO").statusCode()).isEqualTo(200);

        assertThat(avisosDe(habido)).isZero();
        assertThat(avisosDe(noHabido)).isZero();
    }

    @Test
    void siElAvisoFallaLaCondicionDelClienteNoCambia() throws Exception {
        String ruc = rucFicticio();
        long id = crear(ruc, "HABIDO");
        doThrow(new DataAccessResourceFailureException("fallo simulado al guardar el aviso"))
                .when(avisos).crear(eq("HU22_RUC"), anyString(), anyString(), anyList());

        assertThat(editar(id, ruc, "Empresa Prueba T80 SAC", "NO_HABIDO").statusCode()).isGreaterThanOrEqualTo(500);

        assertThat(jdbc.queryForObject("SELECT condicion_habido FROM tributario_schema.clientes WHERE id = ?",
                String.class, id)).isEqualTo("HABIDO");
        assertThat(avisosDe(id)).isZero();
    }
}
