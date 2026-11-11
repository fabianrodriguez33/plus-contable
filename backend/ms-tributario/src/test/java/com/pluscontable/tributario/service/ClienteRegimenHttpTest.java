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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Régimen tributario (RER, decisión de Beto del 10/11) por HTTP real: el alta y la edición aceptan RER, rechazan REMIPE con los valores
 * permitidos y el régimen y la condición se guardan y se devuelven como datos distintos. PostgreSQL real; clientes ficticios que se borran.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ClienteRegimenHttpTest {

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> rucsCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private String token() {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR")).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> llamar(String metodo, String ruta, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token()).header("Content-Type", "application/json");
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cuerpo(String ruc, String regimen, String condicion) {
        return "{\"ruc\":\"" + ruc + "\",\"razonSocial\":\"Cliente ficticio del régimen\",\"regimenTributario\":\"" + regimen + "\""
                + (condicion == null ? "" : ",\"condicionHabido\":\"" + condicion + "\"") + "}";
    }

    private JsonNode cuerpoDe(HttpResponse<String> r) throws Exception {
        return json.readTree(r.body());
    }

    @Test
    void elAltaConRerSeGuardaYSeDevuelveComoRerYLaCondicionVieneAparte() throws Exception {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);

        HttpResponse<String> creado = llamar("POST", "/api/tributario/clientes", cuerpo(ruc, "RER", "NO_HABIDO"));

        assertThat(creado.statusCode()).isEqualTo(201);
        long id = cuerpoDe(creado).get("id").asLong();
        JsonNode leido = cuerpoDe(llamar("GET", "/api/tributario/clientes/" + id, null));
        assertThat(leido.get("regimenTributario").asText()).isEqualTo("RER");
        assertThat(leido.get("condicionHabido").asText()).isEqualTo("NO_HABIDO");
        assertThat(jdbc.queryForObject("SELECT regimen_tributario || '|' || condicion_habido FROM tributario_schema.clientes WHERE id = ?",
                String.class, id)).isEqualTo("RER|NO_HABIDO");
    }

    @Test
    void remipeSeRechazaConLosValoresPermitidosYNoSeGuardaNada() throws Exception {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);

        HttpResponse<String> r = llamar("POST", "/api/tributario/clientes", cuerpo(ruc, "REMIPE", "HABIDO"));

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(cuerpoDe(r).get("mensaje").asText()).contains("regimenTributario", "REMIPE", "MYPE", "RER", "GENERAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE ruc = ?", Integer.class, ruc)).isZero();
    }

    @Test
    void unaCondicionPuestaComoRegimenOUnRegimenPuestoComoCondicionSeRechazan() throws Exception {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);

        assertThat(llamar("POST", "/api/tributario/clientes", cuerpo(ruc, "HABIDO", "HABIDO")).statusCode()).isEqualTo(400);
        assertThat(llamar("POST", "/api/tributario/clientes", cuerpo(ruc, "RER", "RER")).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes WHERE ruc = ?", Integer.class, ruc)).isZero();
    }

    @Test
    void editarSoloElRegimenDejaLaCondicionComoEstabaYAlReves() throws Exception {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        long id = cuerpoDe(llamar("POST", "/api/tributario/clientes", cuerpo(ruc, "MYPE", "NO_HABIDO"))).get("id").asLong();

        JsonNode aRer = cuerpoDe(llamar("PUT", "/api/tributario/clientes/" + id, cuerpo(ruc, "RER", "NO_HABIDO")));
        assertThat(aRer.get("regimenTributario").asText()).isEqualTo("RER");
        assertThat(aRer.get("condicionHabido").asText()).isEqualTo("NO_HABIDO");

        JsonNode aHabido = cuerpoDe(llamar("PUT", "/api/tributario/clientes/" + id, cuerpo(ruc, "RER", "HABIDO")));
        assertThat(aHabido.get("regimenTributario").asText()).isEqualTo("RER");
        assertThat(aHabido.get("condicionHabido").asText()).isEqualTo("HABIDO");
    }

    @Test
    void losTresRegimenesSeAceptanEnElAlta() throws Exception {
        for (String regimen : List.of("MYPE", "RER", "GENERAL")) {
            String ruc = RucFicticio.nuevo();
            rucsCreados.add(ruc);
            HttpResponse<String> r = llamar("POST", "/api/tributario/clientes", cuerpo(ruc, regimen, "HABIDO"));
            assertThat(r.statusCode()).as(regimen).isEqualTo(201);
            assertThat(cuerpoDe(r).get("regimenTributario").asText()).isEqualTo(regimen);
        }
    }
}
