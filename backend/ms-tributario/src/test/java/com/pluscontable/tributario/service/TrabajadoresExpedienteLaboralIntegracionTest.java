package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Trabajador;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-03 (tarea 40): los trabajadores quedan en el expediente laboral del cliente.
 *
 * <p>Usa PostgreSQL y HTTP reales, y NO es transaccional: cada alta se confirma de verdad y se comprueba con otra
 * conexión (JdbcTemplate) y con consultas nuevas por HTTP. Los clientes y trabajadores son ficticios y se borran
 * al terminar (las claves foráneas a clientes son ON DELETE CASCADE). Solo debe correr contra la base de pruebas
 * indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TrabajadoresExpedienteLaboralIntegracionTest {

    private static final String BASE = "/api/tributario/clientes";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpEntity<Object> conToken(Object cuerpo) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        return new HttpEntity<>(cuerpo, cabeceras);
    }

    private long crearCliente(String razonSocial) {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, razonSocial, Cliente.Regimen.MYPE, Cliente.CondicionHabido.HABIDO,
                null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST, conToken(dto), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private static TrabajadorDto nacional(String documento) {
        return new TrabajadorDto(null, "Ana Quispe Ficticia", Trabajador.TipoDocumento.DNI, documento, false, null,
                Trabajador.SistemaPensionario.ONP, null, LocalDate.of(2025, 3, 1), new BigDecimal("1500.00"),
                null, null, null);
    }

    private static TrabajadorDto extranjero(String documento) {
        return new TrabajadorDto(null, "John Ficticio Smith", Trabajador.TipoDocumento.CE, documento, true,
                "Venezolana", Trabajador.SistemaPensionario.AFP, "AFP Ficticia", LocalDate.of(2025, 4, 1),
                new BigDecimal("2200.00"), "PLAZO_FIJO", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
    }

    private ResponseEntity<Map> registrar(long clienteId, TrabajadorDto dto) {
        return http.exchange(BASE + "/" + clienteId + "/trabajadores", HttpMethod.POST, conToken(dto), Map.class);
    }

    private List<TrabajadorDto> listar(long clienteId) {
        ResponseEntity<List<TrabajadorDto>> r = http.exchange(BASE + "/" + clienteId + "/trabajadores",
                HttpMethod.GET, conToken(null), new ParameterizedTypeReference<List<TrabajadorDto>>() { });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private int filasDeTrabajadores(long clienteId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.trabajadores WHERE cliente_id = ?",
                Integer.class, clienteId);
    }

    @Test
    void losTrabajadoresQuedanEnElExpedienteLaboralDeSuClienteYDeNingunOtro() {
        long clienteA = crearCliente("Cliente ficticio A (expediente laboral)");
        long clienteB = crearCliente("Cliente ficticio B (expediente laboral)");
        String dniNacional = "7" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 7);
        String ceExtranjero = "CE" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 8);

        assertThat(registrar(clienteA, nacional(dniNacional)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(clienteA, extranjero(ceExtranjero)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // consulta nueva por HTTP: aparecen en el expediente del cliente A, con sus datos propios
        List<TrabajadorDto> deA = listar(clienteA);
        assertThat(deA).extracting(TrabajadorDto::numeroDocumento).containsExactlyInAnyOrder(dniNacional, ceExtranjero);
        TrabajadorDto ext = deA.stream().filter(TrabajadorDto::extranjero).findFirst().orElseThrow();
        assertThat(ext.nacionalidad()).isEqualTo("Venezolana");
        assertThat(ext.tipoContrato()).isEqualTo("PLAZO_FIJO");
        assertThat(ext.fechaFinContrato()).isEqualTo(LocalDate.of(2026, 3, 31));
        TrabajadorDto nac = deA.stream().filter(t -> !t.extranjero()).findFirst().orElseThrow();
        assertThat(nac.sistemaPensionario()).isEqualTo(Trabajador.SistemaPensionario.ONP);
        assertThat(nac.nacionalidad()).as("los datos de extranjería solo se guardan para el extranjero").isNull();

        // el expediente del otro cliente sigue vacío
        assertThat(listar(clienteB)).isEmpty();

        // y la base lo confirma con otra conexión: las filas llevan el cliente_id correcto
        assertThat(filasDeTrabajadores(clienteA)).isEqualTo(2);
        assertThat(filasDeTrabajadores(clienteB)).isZero();
    }

    @Test
    void noSeRegistraUnTrabajadorSobreUnClienteInexistente() {
        long inexistente = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000000 FROM tributario_schema.clientes", Long.class);
        String documento = "8" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 7);

        ResponseEntity<Map> r = registrar(inexistente, nacional(documento));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(r.getBody().get("mensaje"))).contains("Cliente no encontrado");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.trabajadores WHERE numero_documento = ?",
                Integer.class, documento)).as("no debe quedar ninguna fila huérfana").isZero();
    }

    @Test
    void elMismoDocumentoPuedeEstarEnOtroClientePeroNoRepetirseEnElMismo() {
        long clienteA = crearCliente("Cliente ficticio A (documento repetido)");
        long clienteB = crearCliente("Cliente ficticio B (documento repetido)");
        String documento = "6" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 7);

        assertThat(registrar(clienteA, nacional(documento)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(clienteB, nacional(documento)).getStatusCode())
                .as("otro cliente puede tener a la misma persona en su planilla").isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> repetido = registrar(clienteA, nacional(documento));

        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(repetido.getBody().get("mensaje")))
                .contains("Ya existe un trabajador con ese tipo y número de documento");
        assertThat(filasDeTrabajadores(clienteA)).isEqualTo(1);
        assertThat(filasDeTrabajadores(clienteB)).isEqualTo(1);
    }
}
