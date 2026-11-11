package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.dto.NotificacionSolDto;
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
 * HU-06 · avance parcial T177/T180: marcar inactivo no elimina la ficha ni los registros ya archivados.
 * No prueba ni implementa el cierre. Usa PostgreSQL de pruebas y almacenamiento de archivos temporal.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConservacionClienteInactivoIntegracionTest {

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
            return Files.createTempDirectory("pc-hu06-documentos-");
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

    private HttpHeaders contador(MediaType tipo) {
        var key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("contador-prueba").claim("roles", List.of("CONTADOR"))
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
        var dto = new ClienteDto(null, ruc, "Cliente ficticio HU-06", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST,
                new HttpEntity<>(dto, contador(MediaType.APPLICATION_JSON)), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private void marcarInactivo(long clienteId) {
        assertThat(jdbc.update("UPDATE tributario_schema.clientes SET activo = false WHERE id = ?", clienteId)).isEqualTo(1);
    }

    @Test
    void clienteInactivoPermaneceEnBdYLaFichaSeConsultaPorIdConAutenticacion() {
        long id = crearCliente();
        marcarInactivo(id);

        assertThat(jdbc.queryForObject("SELECT activo FROM tributario_schema.clientes WHERE id = ?", Boolean.class, id))
                .isFalse();
        ResponseEntity<ClienteDto> ficha = http.exchange(BASE + "/" + id, HttpMethod.GET,
                new HttpEntity<>(contador(null)), ClienteDto.class);
        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ficha.getBody().id()).isEqualTo(id);
        assertThat(ficha.getBody().razonSocial()).isEqualTo("Cliente ficticio HU-06");
        assertThat(http.exchange(BASE + "/" + id, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void documentosArchivadosSiguenEnBdYSePuedenListarYDescargarTrasInactivar() {
        long id = crearCliente();
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("tipo", "FICHA_RUC");
        partes.add("fechaEmision", "2026-01-15");
        HttpHeaders cabeceraPdf = new HttpHeaders();
        cabeceraPdf.setContentType(MediaType.APPLICATION_PDF);
        partes.add("archivo", new HttpEntity<>(new ByteArrayResource(PDF) {
            @Override public String getFilename() { return "ficha-ruc.pdf"; }
        }, cabeceraPdf));
        ResponseEntity<DocumentoDto> subida = http.exchange(BASE + "/" + id + "/documentos", HttpMethod.POST,
                new HttpEntity<>(partes, contador(MediaType.MULTIPART_FORM_DATA)), DocumentoDto.class);
        assertThat(subida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long documentoId = subida.getBody().id();
        marcarInactivo(id);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.documentos_legales WHERE cliente_id = ? AND id = ?",
                Integer.class, id, documentoId)).isEqualTo(1);
        ResponseEntity<List<DocumentoDto>> listado = http.exchange(BASE + "/" + id + "/documentos",
                HttpMethod.GET, new HttpEntity<>(contador(null)), new ParameterizedTypeReference<>() { });
        assertThat(listado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listado.getBody()).extracting(DocumentoDto::id).contains(documentoId);
        ResponseEntity<byte[]> descarga = http.exchange(BASE + "/" + id + "/documentos/" + documentoId + "/archivo",
                HttpMethod.GET, new HttpEntity<>(contador(null)), byte[].class);
        assertThat(descarga.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(descarga.getBody()).isEqualTo(PDF);
    }

    @Test
    void notificacionAnteriorSigueDisponibleParaElContadorTrasInactivar() {
        long id = crearCliente();
        LocalDate hoy = LocalDate.now();
        Map<String, String> entrada = Map.of("tipo", "COMUNICADO", "fechaRecepcion", hoy.toString(),
                "plazoRespuesta", hoy.plusDays(5).toString());
        ResponseEntity<NotificacionSolDto> registro = http.exchange("/api/tributario/clientes/" + id + "/notificaciones",
                HttpMethod.POST, new HttpEntity<>(entrada, contador(MediaType.APPLICATION_JSON)), NotificacionSolDto.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long notificacionId = registro.getBody().id();
        marcarInactivo(id);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.notificaciones_sol WHERE cliente_id = ? AND id = ?",
                Integer.class, id, notificacionId)).isEqualTo(1);
        ResponseEntity<List<NotificacionSolDto>> historial = http.exchange("/api/tributario/clientes/" + id + "/notificaciones",
                HttpMethod.GET, new HttpEntity<>(contador(null)), new ParameterizedTypeReference<>() { });
        assertThat(historial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historial.getBody()).extracting(NotificacionSolDto::id).contains(notificacionId);
    }
}
