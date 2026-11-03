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
 * HU-04 (tarea 47): los documentos solo se archivan sobre un cliente registrado.
 *
 * <p>Usa PostgreSQL y HTTP reales y una carpeta de almacenamiento temporal propia (no toca {@code ./uploads}).
 * Comprueba que con un cliente inexistente no queda ni fila en la base ni archivo en disco, y que el mismo envío
 * sí se archiva sobre un cliente registrado (control: así la prueba detectaría que se quitara la validación).
 * Los clientes son ficticios y se borran al terminar. Solo debe correr contra la base de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DocumentoClienteInexistenteIntegracionTest {

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
            return Files.createTempDirectory("pc-uploads-prueba-");
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
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (documentos)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(dto, cabeceras(MediaType.APPLICATION_JSON)), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private <T> ResponseEntity<T> archivar(long clienteId, Class<T> tipoRespuesta) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("tipo", "FICHA_RUC");
        partes.add("fechaEmision", "2026-01-15");
        HttpHeaders cabeceraArchivo = new HttpHeaders();
        cabeceraArchivo.setContentType(MediaType.APPLICATION_PDF);
        partes.add("archivo", new HttpEntity<>(new ByteArrayResource(PDF) {
            @Override
            public String getFilename() {
                return "ficha-ruc.pdf";
            }
        }, cabeceraArchivo));
        return http.exchange(BASE + "/" + clienteId + "/documentos", HttpMethod.POST,
                new HttpEntity<>(partes, cabeceras(MediaType.MULTIPART_FORM_DATA)), tipoRespuesta);
    }

    private static long archivosEnDisco() throws IOException {
        try (Stream<Path> rutas = Files.walk(ALMACENAMIENTO)) {
            return rutas.filter(Files::isRegularFile).count();
        }
    }

    private int documentosDelCliente(long clienteId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.documentos_legales WHERE cliente_id = ?",
                Integer.class, clienteId);
    }

    @Test
    void noSeArchivaUnDocumentoSobreUnClienteInexistente() throws IOException {
        long inexistente = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000000 FROM tributario_schema.clientes", Long.class);
        long archivosAntes = archivosEnDisco();

        ResponseEntity<Map> r = archivar(inexistente, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(String.valueOf(r.getBody().get("mensaje"))).contains("Cliente no encontrado");
        assertThat(documentosDelCliente(inexistente)).as("no debe quedar ninguna fila").isZero();
        assertThat(archivosEnDisco()).as("no debe quedar ningún archivo en disco").isEqualTo(archivosAntes);
        assertThat(Files.exists(ALMACENAMIENTO.resolve(String.valueOf(inexistente)))).isFalse();
    }

    @Test
    void elMismoEnvioSiSeArchivaSobreUnClienteRegistrado() throws IOException {
        long cliente = crearCliente();
        long archivosAntes = archivosEnDisco();

        ResponseEntity<DocumentoDto> r = archivar(cliente, DocumentoDto.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().version()).isEqualTo(1);
        assertThat(documentosDelCliente(cliente)).isEqualTo(1);
        assertThat(archivosEnDisco()).isEqualTo(archivosAntes + 1);

        // y se ve en el expediente del cliente con su fecha de emisión (consulta nueva)
        ResponseEntity<List<DocumentoDto>> lista = http.exchange(BASE + "/" + cliente + "/documentos", HttpMethod.GET,
                new HttpEntity<>(cabeceras(null)), new ParameterizedTypeReference<List<DocumentoDto>>() { });
        assertThat(lista.getBody()).singleElement().satisfies(d ->
                assertThat(d.fechaEmision().toString()).isEqualTo("2026-01-15"));
    }
}
