package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-02 (criterio «se puede editar el aporte»): el porcentaje de cada socio se calcula según su aporte y se
 * recalcula al editar uno. Prueba de integración con PostgreSQL y HTTP reales: se registra, se edita y se lee por la
 * API, y se comprueba también lo que quedó guardado en la tabla. Usa clientes ficticios que se borran al terminar
 * (los socios se borran en cascada). Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SociosPorcentajeIntegracionTest {

    private static final String BASE = "/api/tributario/clientes";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();

    @BeforeEach
    void permitirPatch() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpHeaders cabeceras() {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (porcentaje de socios)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST, new HttpEntity<>(dto, cabeceras()), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private static Map<String, Object> socio(String nombre, String documento, Object aporte) {
        return Map.of("nombreCompleto", nombre, "numeroDocumento", documento, "aporte", aporte);
    }

    private ResponseEntity<ResumenSocietarioDto> guardar(long cliente, List<Map<String, Object>> socios) {
        return http.exchange(BASE + "/" + cliente + "/socios", HttpMethod.PUT, new HttpEntity<>(socios, cabeceras()),
                ResumenSocietarioDto.class);
    }

    private ResponseEntity<ResumenSocietarioDto> editarAporte(long cliente, long socio, Object aporte) {
        return http.exchange(BASE + "/" + cliente + "/socios/" + socio + "/aporte", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("aporte", aporte), cabeceras()), ResumenSocietarioDto.class);
    }

    private ResumenSocietarioDto resumen(long cliente) {
        ResponseEntity<ResumenSocietarioDto> r = http.exchange(BASE + "/" + cliente + "/socios/resumen", HttpMethod.GET,
                new HttpEntity<>(cabeceras()), ResumenSocietarioDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<String> porcentajesGuardados(long cliente) {
        return jdbc.queryForList("SELECT porcentaje FROM tributario_schema.socios WHERE cliente_id = ? ORDER BY id",
                BigDecimal.class, cliente).stream().map(BigDecimal::toPlainString).toList();
    }

    @Test
    void elPorcentajeSeCalculaAlGuardarYQuedaGuardadoEnLaBase() {
        long cliente = crearCliente();

        ResponseEntity<ResumenSocietarioDto> r = guardar(cliente, List.of(
                socio("Ana", "1", 60000), socio("Luis", "2", 40000)));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("60.00", "40.00");
        assertThat(r.getBody().completo()).isTrue();
        assertThat(porcentajesGuardados(cliente)).containsExactly("60.00", "40.00");
    }

    @Test
    void editarElAporteRecalculaYGuardaElPorcentajeDeTodosLosSocios() {
        long cliente = crearCliente();
        List<SocioDto> guardados = guardar(cliente, List.of(socio("Ana", "1", 60000), socio("Luis", "2", 40000)))
                .getBody().socios();

        ResponseEntity<ResumenSocietarioDto> r = editarAporte(cliente, guardados.get(0).id(), 75000);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().capitalSocialTotal()).isEqualByComparingTo("115000.00");
        assertThat(r.getBody().socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("65.22", "34.78");
        assertThat(r.getBody().porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(r.getBody().completo()).isTrue();
        // lo que se lee después y lo que hay en la tabla coincide con la respuesta
        assertThat(resumen(cliente).socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("65.22", "34.78");
        assertThat(porcentajesGuardados(cliente)).containsExactly("65.22", "34.78");
    }

    @Test
    void elResiduoDeTresAportesIgualesSeGuardaPorMayorRestoYOrdenDeAlta() {
        long cliente = crearCliente();

        ResumenSocietarioDto r = guardar(cliente, List.of(
                socio("A", "1", 1000), socio("B", "2", 1000), socio("C", "3", 1000))).getBody();

        assertThat(r.socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("33.34", "33.33", "33.33");
        assertThat(porcentajesGuardados(cliente)).containsExactly("33.34", "33.33", "33.33");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void siSeEditaVariasVecesLaSumaSiempreEs100() {
        long cliente = crearCliente();
        List<SocioDto> socios = guardar(cliente, List.of(
                socio("A", "1", 1000), socio("B", "2", 1000), socio("C", "3", 1000))).getBody().socios();

        for (int i = 0; i < 6; i++) {
            ResumenSocietarioDto r = editarAporte(cliente, socios.get(i % 3).id(), 1000 + 137 * (i + 1)).getBody();
            assertThat(r.porcentajeTotal()).as("tras la edición %d", i + 1).isEqualByComparingTo("100.00");
            assertThat(r.completo()).isTrue();
        }
        BigDecimal sumaEnBase = jdbc.queryForObject(
                "SELECT SUM(porcentaje) FROM tributario_schema.socios WHERE cliente_id = ?", BigDecimal.class, cliente);
        assertThat(sumaEnBase).isEqualByComparingTo("100.00");
    }

    @Test
    void unAporteEnCeroSeRechazaConMensajeYNoCambiaLoGuardado() {
        long cliente = crearCliente();
        List<SocioDto> guardados = guardar(cliente, List.of(socio("Ana", "1", 60000), socio("Luis", "2", 40000)))
                .getBody().socios();

        ResponseEntity<Map> alta = http.exchange(BASE + "/" + cliente + "/socios", HttpMethod.PUT,
                new HttpEntity<>(List.of(socio("Ana", "1", 100), socio("Luis", "2", 0)), cabeceras()), Map.class);
        ResponseEntity<Map> edicion = http.exchange(BASE + "/" + cliente + "/socios/" + guardados.get(0).id() + "/aporte",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("aporte", 0), cabeceras()), Map.class);

        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(edicion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(edicion.getBody().get("mensaje").toString()).contains("mayor a 0");
        assertThat(porcentajesGuardados(cliente)).containsExactly("60.00", "40.00");
        assertThat(resumen(cliente).capitalSocialTotal()).isEqualByComparingTo("100000.00");
    }

    @Test
    void unPorcentajeEnviadoPorElClienteSeIgnora() {
        long cliente = crearCliente();

        ResumenSocietarioDto r = guardar(cliente, List.of(
                Map.of("nombreCompleto", "Ana", "numeroDocumento", "1", "aporte", 3000, "porcentaje", 99),
                Map.of("nombreCompleto", "Luis", "numeroDocumento", "2", "aporte", 1000, "porcentaje", 1))).getBody();

        assertThat(r.socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("75.00", "25.00");
    }

    @Test
    void losSociosAnterioresConAporteCeroQuedanIncompletosSinMigrar() {
        long cliente = crearCliente();
        jdbc.update("INSERT INTO tributario_schema.socios (cliente_id, nombre_completo, tipo_documento, numero_documento, aporte, porcentaje)"
                + " VALUES (?, 'Ana anterior', 'DNI', '1', 0, 60), (?, 'Luis anterior', 'DNI', '2', 0, 40)", cliente, cliente);

        ResumenSocietarioDto r = resumen(cliente);

        assertThat(r.completo()).isFalse();
        assertThat(r.sociosSinAporte()).isEqualTo(2);
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(porcentajesGuardados(cliente)).containsExactly("60.00", "40.00");

        // al registrar el aporte de uno solo, el otro sigue sin aporte: se guarda el aporte y sigue incompleto
        long primero = r.socios().get(0).id();
        ResumenSocietarioDto tras = editarAporte(cliente, primero, 5000).getBody();
        assertThat(tras.sociosSinAporte()).isEqualTo(1);
        assertThat(tras.completo()).isFalse();
        assertThat(porcentajesGuardados(cliente)).containsExactly("60.00", "40.00");

        // al completar el último aporte se calculan los porcentajes de todos
        long segundo = r.socios().get(1).id();
        ResumenSocietarioDto completo = editarAporte(cliente, segundo, 15000).getBody();
        assertThat(completo.completo()).isTrue();
        assertThat(completo.socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("25.00", "75.00");
    }
}
