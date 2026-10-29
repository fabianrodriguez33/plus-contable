package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluscontable.tributario.service.TrabajadorService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Los errores deben llegar al cliente con su código real (409, 400, 404, 500) y un mensaje claro, no como 401.
 * Usa un servidor HTTP real porque el reenvío del error a /error no ocurre con MockMvc.
 * Los servicios que fallan van simulados: no se escribe nada en la base (solo se carga el contexto).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ManejoDeErroresHttpTest {

    private static final String TRABAJADORES = "/api/tributario/clientes/1/trabajadores";
    private static final String TRABAJADOR_JSON = """
            {"nombreCompleto":"Ana","tipoDocumento":"DNI","numeroDocumento":"40111222",
             "sistemaPensionario":"ONP","fechaIngreso":"2026-01-01"}""";

    @LocalServerPort int puerto;
    @Value("${app.jwt.secret}") String secreto;
    @MockBean TrabajadorService trabajadorService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private String token(String rol) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("usuario").claim("roles", List.of(rol))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    private HttpResponse<String> llamar(String metodo, String ruta, String cuerpo, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? BodyPublishers.noBody() : BodyPublishers.ofString(cuerpo));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), BodyHandlers.ofString());
    }

    private String mensaje(HttpResponse<String> r) throws Exception {
        JsonNode n = json.readTree(r.body());
        return n.path("mensaje").asText();
    }

    private static DataIntegrityViolationException unicidadViolada(String restriccion) {
        return new DataIntegrityViolationException("no se pudo guardar",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate", "23505"), restriccion));
    }

    @Test
    void trabajadorConDocumentoRepetidoResponde409ConMensajeClaro() throws Exception {
        when(trabajadorService.registrar(anyLong(), any())).thenThrow(unicidadViolada("uq_trabajadores_cliente_documento"));

        HttpResponse<String> r = llamar("POST", TRABAJADORES, TRABAJADOR_JSON, token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(mensaje(r)).isEqualTo("Ya existe un trabajador con ese tipo y número de documento en este cliente");
    }

    @Test
    void restriccionDeUnicidadDesconocidaResponde409Generico() throws Exception {
        when(trabajadorService.registrar(anyLong(), any())).thenThrow(unicidadViolada("otra_restriccion"));

        HttpResponse<String> r = llamar("POST", TRABAJADORES, TRABAJADOR_JSON, token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(mensaje(r)).isEqualTo("La operación viola una restricción de integridad de los datos");
    }

    @Test
    void regimenInvalidoResponde400IndicandoLosValoresPermitidos() throws Exception {
        String cuerpo = "{\"ruc\":\"20555555552\",\"razonSocial\":\"X\",\"regimenTributario\":\"XXX\"}";

        HttpResponse<String> r = llamar("POST", "/api/tributario/clientes", cuerpo, token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(mensaje(r)).contains("regimenTributario").contains("XXX").contains("GENERAL", "MYPE", "REMIPE");
    }

    @Test
    void jsonMalformadoResponde400() throws Exception {
        HttpResponse<String> r = llamar("POST", TRABAJADORES, "{no es json", token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(mensaje(r)).isEqualTo("El cuerpo de la solicitud no es un JSON válido o está vacío");
    }

    @Test
    void rutaInexistenteConTokenValidoResponde404() throws Exception {
        HttpResponse<String> r = llamar("GET", "/api/tributario/no-existe", null, token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(404);
    }

    @Test
    void errorInternoResponde500() throws Exception {
        when(trabajadorService.listar(anyLong())).thenThrow(new IllegalStateException("falla inesperada"));

        HttpResponse<String> r = llamar("GET", TRABAJADORES, null, token("CONTADOR"));

        assertThat(r.statusCode()).isEqualTo(500);
    }

    @Test
    void sinTokenSigueRespondiendo401() throws Exception {
        assertThat(llamar("GET", TRABAJADORES, null, null).statusCode()).isEqualTo(401);
        assertThat(llamar("POST", "/api/tributario/clientes", "{}", null).statusCode()).isEqualTo(401);
    }

    @Test
    void laRutaErrorNoQuedaAbiertaAlPublico() throws Exception {
        assertThat(llamar("GET", "/error", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void tokenFalsificadoSigueRespondiendo401() throws Exception {
        String falso = Jwts.builder().subject("x").claim("roles", List.of("CONTADOR"))
                .signWith(Keys.hmacShaKeyFor("otra-clave-que-no-es-la-del-estudio-32b".getBytes())).compact();

        assertThat(llamar("GET", TRABAJADORES, null, falso).statusCode()).isEqualTo(401);
    }
}
