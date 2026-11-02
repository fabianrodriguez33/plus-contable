package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;
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
 * HU-01 (tarea 20): un cliente guardado aparece en la cartera y persiste.
 *
 * <p>Usa PostgreSQL y HTTP reales, y NO es transaccional: el alta se confirma de verdad, igual que en uso
 * normal. La persistencia se comprueba con otra conexión (JdbcTemplate) y con una consulta nueva por HTTP,
 * no con el objeto que devolvió el alta. El cliente es ficticio, con un RUC de dígito verificador válido
 * generado aquí, y se borra al terminar. Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ClienteCarteraPersistenciaTest {

    private static final int[] FACTORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final String RAZON_SOCIAL = "Cliente ficticio de cartera S.A.C.";

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClienteService clienteService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String ruc;

    /** RUC ficticio de persona jurídica (prefijo 20) con dígito verificador módulo 11 válido. */
    private static String rucFicticioValido() {
        String cuerpo = "20" + String.format("%08d", System.nanoTime() % 100_000_000L);
        int suma = 0;
        for (int i = 0; i < FACTORES.length; i++) {
            suma += (cuerpo.charAt(i) - '0') * FACTORES[i];
        }
        int dv = 11 - (suma % 11);
        return cuerpo + (dv == 10 ? 0 : dv == 11 ? 1 : dv);
    }

    @BeforeEach
    void elegirRuc() {
        ruc = rucFicticioValido();
        assertThat(RucValidator.esValido(ruc)).as("el RUC ficticio debe ser válido").isTrue();
        borrarCliente();
    }

    @AfterEach
    void borrarCliente() {
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private String token() {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
    }

    private HttpResponse<String> llamar(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token())
                .method(metodo, cuerpo == null ? BodyPublishers.noBody() : BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), BodyHandlers.ofString());
    }

    private JsonNode buscarEnLaCartera(String rucBuscado) throws Exception {
        HttpResponse<String> lista = llamar("GET", "/api/tributario/clientes", null);
        assertThat(lista.statusCode()).isEqualTo(200);
        return StreamSupport.stream(json.readTree(lista.body()).spliterator(), false)
                .filter(c -> rucBuscado.equals(c.path("ruc").asText()))
                .findFirst().orElse(null);
    }

    @Test
    void elClienteGuardadoApareceEnLaCarteraYPersisteEnLaBase() throws Exception {
        String cuerpo = """
                {"ruc":"%s","razonSocial":"%s","regimenTributario":"MYPE","direccion":"Av. Ficticia 123"}
                """.formatted(ruc, RAZON_SOCIAL);

        HttpResponse<String> alta = llamar("POST", "/api/tributario/clientes", cuerpo);
        assertThat(alta.statusCode()).isBetween(200, 299);
        long id = json.readTree(alta.body()).path("id").asLong();
        assertThat(id).isPositive();

        // Aparece en la cartera (consulta nueva, no el objeto del alta).
        JsonNode enCartera = buscarEnLaCartera(ruc);
        assertThat(enCartera).as("el cliente aparece en la cartera").isNotNull();
        assertThat(enCartera.path("id").asLong()).isEqualTo(id);
        assertThat(enCartera.path("razonSocial").asText()).isEqualTo(RAZON_SOCIAL);
        assertThat(enCartera.path("regimenTributario").asText()).isEqualTo("MYPE");
        assertThat(enCartera.path("claveSol").isNull()).as("la cartera nunca devuelve la clave SOL").isTrue();

        // Persiste: otra conexión a la base ve la fila ya confirmada.
        var fila = jdbc.queryForMap(
                "SELECT razon_social, regimen_tributario FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        assertThat(fila.get("razon_social")).isEqualTo(RAZON_SOCIAL);
        assertThat(fila.get("regimen_tributario")).isEqualTo("MYPE");

        // Y sigue ahí en una consulta posterior, por la lista y por el detalle.
        assertThat(buscarEnLaCartera(ruc)).isNotNull();
        HttpResponse<String> detalle = llamar("GET", "/api/tributario/clientes/" + id, null);
        assertThat(detalle.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detalle.body()).path("ruc").asText()).isEqualTo(ruc);
    }

    @Test
    void elServicioListaElClienteDespuesDeConfirmarElAlta() {
        ClienteDto creado = clienteService.crear(new ClienteDto(null, ruc, RAZON_SOCIAL,
                Cliente.Regimen.GENERAL, null, null, null, null, null, null, null));

        assertThat(clienteService.listar()).extracting(ClienteDto::ruc).contains(ruc);
        assertThat(clienteService.obtener(creado.id()).razonSocial()).isEqualTo(RAZON_SOCIAL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE ruc = ?",
                Integer.class, ruc)).isEqualTo(1);
    }
}
