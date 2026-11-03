package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * HU-04 (tarea 46): el documento se ve en el expediente del cliente con su fecha de emisión.
 *
 * <p>Prueba de integración con PostgreSQL y HTTP reales: se sube por la API y se lee el listado por la API, de modo
 * que la fecha de emisión recorre todo el camino (formulario, servicio, base de datos y respuesta JSON). Usa una
 * carpeta de almacenamiento temporal propia (no toca {@code ./uploads}) y clientes ficticios que se borran al
 * terminar. Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ListadoDocumentosIntegracionTest {

    private static final String BASE = "/api/tributario/clientes";
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    private static final Path ALMACENAMIENTO = crearAlmacenamiento();

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secreto;

    private final List<String> rucsCreados = new ArrayList<>();

    private static Path crearAlmacenamiento() {
        try {
            return Files.createTempDirectory("pc-uploads-listado-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void carpetaDeArchivos(DynamicPropertyRegistry registro) {
        registro.add("app.storage.dir", ALMACENAMIENTO::toString);
    }

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    @AfterAll
    static void borrarCarpetaTemporal() throws IOException {
        try (Stream<Path> rutas = Files.walk(ALMACENAMIENTO)) {
            rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private HttpHeaders cabeceras(MediaType tipo) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("contador1").claim("roles", List.of("CONTADOR"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        if (tipo != null) h.setContentType(tipo);
        return h;
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (listado de documentos)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(dto, cabeceras(MediaType.APPLICATION_JSON)), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private DocumentoDto archivar(long clienteId, String tipo, String fechaEmision, String nombreArchivo) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("tipo", tipo);
        partes.add("fechaEmision", fechaEmision);
        HttpHeaders cabeceraArchivo = new HttpHeaders();
        cabeceraArchivo.setContentType(MediaType.APPLICATION_PDF);
        partes.add("archivo", new HttpEntity<>(new ByteArrayResource(PDF) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        }, cabeceraArchivo));
        ResponseEntity<DocumentoDto> r = http.exchange(BASE + "/" + clienteId + "/documentos", HttpMethod.POST,
                new HttpEntity<>(partes, cabeceras(MediaType.MULTIPART_FORM_DATA)), DocumentoDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private List<DocumentoDto> listar(long clienteId) {
        ResponseEntity<List<DocumentoDto>> r = http.exchange(BASE + "/" + clienteId + "/documentos", HttpMethod.GET,
                new HttpEntity<>(cabeceras(null)), new ParameterizedTypeReference<List<DocumentoDto>>() { });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    void elDocumentoArchivadoSeVeEnElExpedienteConSuFechaDeEmision() {
        long cliente = crearCliente();
        DocumentoDto subido = archivar(cliente, "FICHA_RUC", "2026-01-15", "ficha-ruc.pdf");

        List<DocumentoDto> expediente = listar(cliente);

        assertThat(expediente).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(subido.id());
            assertThat(d.tipo().name()).isEqualTo("FICHA_RUC");
            assertThat(d.version()).isEqualTo(1);
            assertThat(d.fechaEmision()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(d.nombreArchivo()).isEqualTo("ficha-ruc.pdf");
            assertThat(d.tamanoBytes()).isEqualTo(PDF.length);
            assertThat(d.subidoEn()).isNotNull();
        });
    }

    @Test
    void laFechaDeEmisionLlegaAlClienteWebComoTextoISO() {
        long cliente = crearCliente();
        archivar(cliente, "MINUTA", "2025-12-31", "minuta.pdf");

        ResponseEntity<List<Map<String, Object>>> r = http.exchange(BASE + "/" + cliente + "/documentos", HttpMethod.GET,
                new HttpEntity<>(cabeceras(null)), new ParameterizedTypeReference<List<Map<String, Object>>>() { });

        assertThat(r.getBody()).singleElement().satisfies(d ->
                assertThat(d.get("fechaEmision")).as("la pantalla recibe 'AAAA-MM-DD'").isEqualTo("2025-12-31"));
    }

    @Test
    void cadaVersionConservaSuPropiaFechaDeEmisionYLaVigenteVaPrimero() {
        long cliente = crearCliente();
        archivar(cliente, "VIGENCIA_PODERES", "2025-03-10", "poderes-v1.pdf");
        archivar(cliente, "VIGENCIA_PODERES", "2026-02-20", "poderes-v2.pdf");

        List<DocumentoDto> expediente = listar(cliente);

        assertThat(expediente).extracting(DocumentoDto::version).containsExactly(2, 1);
        assertThat(expediente).extracting(DocumentoDto::fechaEmision)
                .containsExactly(LocalDate.of(2026, 2, 20), LocalDate.of(2025, 3, 10));
        assertThat(expediente).extracting(DocumentoDto::nombreArchivo).containsExactly("poderes-v2.pdf", "poderes-v1.pdf");
    }

    @Test
    void elListadoSeOrdenaPorTipoYPorVersionDescendente() {
        long cliente = crearCliente();
        archivar(cliente, "MINUTA", "2024-05-01", "minuta-v1.pdf");
        archivar(cliente, "FICHA_RUC", "2026-01-15", "ficha.pdf");
        archivar(cliente, "MINUTA", "2025-06-02", "minuta-v2.pdf");

        List<DocumentoDto> expediente = listar(cliente);

        assertThat(expediente).extracting(d -> d.tipo().name() + " v" + d.version())
                .containsExactly("FICHA_RUC v1", "MINUTA v2", "MINUTA v1");
    }

    @Test
    void elExpedienteSoloMuestraLosDocumentosDeEseCliente() {
        long a = crearCliente();
        long b = crearCliente();
        DocumentoDto deA1 = archivar(a, "FICHA_RUC", "2026-01-15", "a-ficha.pdf");
        DocumentoDto deA2 = archivar(a, "MINUTA", "2025-01-10", "a-minuta.pdf");
        DocumentoDto deB = archivar(b, "FICHA_RUC", "2026-02-01", "b-ficha.pdf");

        assertThat(listar(a)).extracting(DocumentoDto::id).containsExactlyInAnyOrder(deA1.id(), deA2.id());
        assertThat(listar(b)).extracting(DocumentoDto::id).containsExactly(deB.id());
    }

    @Test
    void unClienteSinDocumentosTieneUnExpedienteVacio() {
        long cliente = crearCliente();

        assertThat(listar(cliente)).isEmpty();
    }

    @Test
    void elExpedienteDeUnClienteInexistenteDa404() {
        long inexistente = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000000 FROM tributario_schema.clientes", Long.class);

        ResponseEntity<Map> r = http.exchange(BASE + "/" + inexistente + "/documentos", HttpMethod.GET,
                new HttpEntity<>(cabeceras(null)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(r.getBody().get("mensaje"))).contains("Cliente no encontrado");
    }

    @Test
    void sinSesionNoSeVeElExpediente() {
        long cliente = crearCliente();
        archivar(cliente, "FICHA_RUC", "2026-01-15", "ficha.pdf");

        ResponseEntity<String> r = http.exchange(BASE + "/" + cliente + "/documentos", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
