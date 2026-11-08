package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.model.Cliente;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * HT-02 (tarea 110): servicio de cifrado AES-256 de la Clave SOL, de punta a punta. Prueba de integración con
 * PostgreSQL y HTTP reales: se guarda y se lee por la API y se mira lo que quedó en la tabla {@code credenciales_sol}
 * (cifrado, sin la llave y sin el texto), qué pasa con otra llave o con un valor alterado, y los permisos de las rutas.
 * Usa clientes ficticios que se borran al terminar (la Clave SOL se borra en cascada). Solo debe correr contra la base
 * de pruebas indicada por el POM.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ClaveSolIntegracionTest {

    private static final String BASE = "/api/tributario/clientes";
    private static final String USUARIO_SOL = "USUARIO-DE-PRUEBA-HT02";
    private static final String CLAVE_SOL = "clave-de-prueba-HT02-9X";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") String secretoJwt;
    @Value("${app.aes.secret-key}") String llaveAes;
    @Value("${app.aes.legacy-iv}") String ivLegado;

    private final List<String> rucsCreados = new ArrayList<>();

    @BeforeEach
    void permitirPut() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void borrarClientesFicticios() {
        for (String ruc : rucsCreados) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private HttpHeaders cabeceras(List<String> roles, Long clienteId) {
        var key = Keys.hmacShaKeyFor(secretoJwt.getBytes(StandardCharsets.UTF_8));
        var token = Jwts.builder().subject("usuario-prueba").claim("roles", roles)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000));
        if (clienteId != null) token.claim("clienteId", clienteId);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token.signWith(key).compact());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders contador() {
        return cabeceras(List.of("CONTADOR"), null);
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucsCreados.add(ruc);
        var dto = new ClienteDto(null, ruc, "Cliente ficticio (Clave SOL)", Cliente.Regimen.MYPE,
                Cliente.CondicionHabido.HABIDO, null, null, null, null, null, null);
        ResponseEntity<ClienteDto> r = http.exchange(BASE, HttpMethod.POST, new HttpEntity<>(dto, contador()), ClienteDto.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().id();
    }

    private ResponseEntity<String> guardarSol(long cliente, Object cuerpo, HttpHeaders cab) {
        return http.exchange(BASE + "/" + cliente + "/sol", HttpMethod.PUT, new HttpEntity<>(cuerpo, cab), String.class);
    }

    private ResponseEntity<String> leerSol(long cliente, HttpHeaders cab) {
        return http.exchange(BASE + "/" + cliente + "/sol", HttpMethod.GET, new HttpEntity<>(cab), String.class);
    }

    private static Map<String, String> sol(String usuario, String clave) {
        return Map.of("usuarioSol", usuario, "claveSol", clave);
    }

    private Map<String, Object> filaGuardada(long cliente) {
        return jdbc.queryForMap("SELECT usuario_sol_cifrado, clave_sol_cifrada FROM tributario_schema.credenciales_sol WHERE cliente_id = ?", cliente);
    }

    @Test
    void laClaveSolGuardadaQuedaCifradaEnLaBaseYSeLeeDeVuelta() {
        long cliente = crearCliente();

        assertThat(guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), contador()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> fila = filaGuardada(cliente);
        String usuarioCifrado = (String) fila.get("usuario_sol_cifrado");
        String claveCifrada = (String) fila.get("clave_sol_cifrada");
        assertThat(usuarioCifrado).startsWith(AesUtil.PREFIJO_V2).doesNotContain(USUARIO_SOL);
        assertThat(claveCifrada).startsWith(AesUtil.PREFIJO_V2).doesNotContain(CLAVE_SOL);
        // la llave no está en la base: ni en la fila guardada ni como texto de ninguna de sus columnas
        assertThat(usuarioCifrado + claveCifrada).doesNotContain(llaveAes);
        ResponseEntity<String> lectura = leerSol(cliente, contador());
        assertThat(lectura.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lectura.getBody()).contains(USUARIO_SOL).contains(CLAVE_SOL);
    }

    @Test
    void guardarLaMismaClaveDosVecesDaTextosCifradosDistintosYSigueSiendoLegible() {
        long cliente = crearCliente();

        guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), contador());
        String primera = (String) filaGuardada(cliente).get("clave_sol_cifrada");
        guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), contador());
        String segunda = (String) filaGuardada(cliente).get("clave_sol_cifrada");

        assertThat(segunda).isNotEqualTo(primera);
        assertThat(leerSol(cliente, contador()).getBody()).contains(CLAVE_SOL);
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM tributario_schema.credenciales_sol WHERE cliente_id = ?", Integer.class, cliente);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void unValorCifradoConOtraLlaveNoSeLeeYLaRespuestaNoMuestraNada() {
        long cliente = crearCliente();
        AesUtil otraLlave = new AesUtil("OtraLlaveDePruebasAES256Solo!!!!", ivLegado);
        jdbc.update("INSERT INTO tributario_schema.credenciales_sol (cliente_id, usuario_sol_cifrado, clave_sol_cifrada) VALUES (?, ?, ?)",
                cliente, otraLlave.encrypt(USUARIO_SOL), otraLlave.encrypt(CLAVE_SOL));

        ResponseEntity<String> r = leerSol(cliente, contador());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(r.getBody())).doesNotContain(USUARIO_SOL).doesNotContain(CLAVE_SOL)
                .doesNotContain(llaveAes).doesNotContain("Error al descifrar");
    }

    @Test
    void unValorAlteradoEnLaBaseTampocoSeLee() {
        long cliente = crearCliente();
        guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), contador());
        String claveCifrada = (String) filaGuardada(cliente).get("clave_sol_cifrada");
        int medio = claveCifrada.length() / 2;
        char cambio = claveCifrada.charAt(medio) == 'A' ? 'B' : 'A';
        String alterada = claveCifrada.substring(0, medio) + cambio + claveCifrada.substring(medio + 1);
        jdbc.update("UPDATE tributario_schema.credenciales_sol SET clave_sol_cifrada = ? WHERE cliente_id = ?", alterada, cliente);

        ResponseEntity<String> r = leerSol(cliente, contador());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(r.getBody())).doesNotContain(USUARIO_SOL).doesNotContain(CLAVE_SOL);
    }

    @Test
    void sinSesionResponde401EnLasDosRutas() {
        long cliente = crearCliente();

        assertThat(http.exchange(BASE + "/" + cliente + "/sol", HttpMethod.PUT, new HttpEntity<>(sol(USUARIO_SOL, CLAVE_SOL)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange(BASE + "/" + cliente + "/sol", HttpMethod.GET, HttpEntity.EMPTY, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM tributario_schema.credenciales_sol WHERE cliente_id = ?", Integer.class, cliente);
        assertThat(filas).isZero();
    }

    @Test
    void unClienteMypeNoGuardaNiLeeLaClaveSolNiLaDeSuPropiaEmpresa() {
        long cliente = crearCliente();
        guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), contador());
        HttpHeaders mype = cabeceras(List.of("CLIENTE_MYPE"), cliente);

        ResponseEntity<String> lectura = leerSol(cliente, mype);
        ResponseEntity<String> escritura = guardarSol(cliente, sol("otro", "otra"), mype);

        assertThat(lectura.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(escritura.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(String.valueOf(lectura.getBody())).doesNotContain(CLAVE_SOL);
        assertThat(leerSol(cliente, contador()).getBody()).contains(CLAVE_SOL); // no se modificó
    }

    @Test
    void elAdministradorDelEstudioTambienPuedeGuardarYLeer() {
        long cliente = crearCliente();
        HttpHeaders admin = cabeceras(List.of("ADMIN"), null);

        assertThat(guardarSol(cliente, sol(USUARIO_SOL, CLAVE_SOL), admin).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(leerSol(cliente, admin).getBody()).contains(CLAVE_SOL);
    }

    @Test
    void sinClaveRegistradaOConDatosIncompletosOClienteInexistenteResponde404Y400() {
        long cliente = crearCliente();

        ResponseEntity<String> sinClave = leerSol(cliente, contador());
        ResponseEntity<String> incompleta = guardarSol(cliente, sol(USUARIO_SOL, " "), contador());
        ResponseEntity<String> inexistente = guardarSol(Long.MAX_VALUE, sol(USUARIO_SOL, CLAVE_SOL), contador());

        assertThat(sinClave.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(sinClave.getBody()).contains("no tiene Clave SOL registrada");
        assertThat(incompleta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM tributario_schema.credenciales_sol WHERE cliente_id = ?", Integer.class, cliente);
        assertThat(filas).isZero();
    }
}
